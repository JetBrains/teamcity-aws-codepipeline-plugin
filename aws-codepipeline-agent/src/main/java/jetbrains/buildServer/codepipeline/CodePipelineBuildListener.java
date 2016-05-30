/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.codepipeline;

import com.amazonaws.services.codepipeline.model.*;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSClients;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static jetbrains.buildServer.codepipeline.CodePipelineUtil.getArtifactPaths;
import static jetbrains.buildServer.codepipeline.CodePipelineUtil.getJobId;
import static jetbrains.buildServer.util.amazon.AWSCommonParams.createAWSClients;

/**
 * @author vbedrosova
 */
public class CodePipelineBuildListener extends AgentLifeCycleAdapter {
  @NotNull
  private static final Logger LOG = Logger.getInstance(Loggers.VCS_CATEGORY + CodePipelineBuildListener.class);

  private boolean myJobInputProcessed;
  private String myJobID;

  public CodePipelineBuildListener(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher) {
    agentDispatcher.addListener(this);
  }

  @Override
  public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
    myJobInputProcessed = false;
    myJobID = null;
  }

  @Override
  public void sourcesUpdated(@NotNull AgentRunningBuild runningBuild) {
    processJobInput(runningBuild);
  }

  @Override
  public void beforeRunnerStart(@NotNull BuildRunnerContext runner) {
    processJobInput(runner.getBuild());
  }

  @Override
  public void beforeBuildFinish(@NotNull AgentRunningBuild build, @NotNull BuildFinishedStatus buildStatus) {
    processJobOutput(build);
  }

  private void processJobInput(@NotNull AgentRunningBuild build) {
    if (myJobInputProcessed) return;
    myJobInputProcessed = true;

    try {
      final Map<String, String> params = build.getSharedConfigParameters();
      myJobID = getJobId(params);
      if (myJobID == null) {
        LOG.debug(msgForBuild("No AWS CodePipeline job found for the build", build));
        return;
      }

      final JobData jobData = getJobData(params);

      validateOutputArtifacts(jobData.getOutputArtifacts(), getArtifactPaths(params));

      final PipelineContext pipelineContext = jobData.getPipelineContext();
      build.getBuildLogger().message(
        "This build is a part of an AWS CodePipeline pipeline: " + pipelineContext.getPipelineName() +
          "\nStage: " + pipelineContext.getStage().getName() +
          "\nAction: " + pipelineContext.getAction().getName() +
          "\nJob ID: " + myJobID);

      final List<Artifact> inputArtifacts = jobData.getInputArtifacts();
      if (inputArtifacts.isEmpty()) {
        LOG.debug(msgForBuild("No input artifacts provided for the job with ID: " + myJobID, build));
      } else {
        for (Artifact artifact : inputArtifacts) {
          final S3ArtifactLocation s3Location = artifact.getLocation().getS3Location();
          final File destinationFile = new File(build.getBuildTempDirectory(), s3Location.getObjectKey());

          build.getBuildLogger().message("Downloading job input artifact " + s3Location.getObjectKey() + " to " + destinationFile.getAbsolutePath());
          getArtifactS3Client(jobData.getArtifactCredentials(), params)
            .getObject(new GetObjectRequest(s3Location.getBucketName(), s3Location.getObjectKey()), destinationFile);
        }
      }
    } catch (Throwable e) {
      failOnException(build, e);
    }
  }

  private void processJobOutput(@NotNull AgentRunningBuild build) {
    if (myJobID == null) return;

    try {
      if (build.isBuildFailingOnServer()) {
        publishJobFailure(build, "Build failed");
      } else {
        final Map<String, String> params = build.getSharedConfigParameters();
        final JobData jobData = getJobData(params);

        final List<Artifact> outputArtifacts = jobData.getOutputArtifacts();
        if (outputArtifacts.isEmpty()) {
          LOG.debug(msgForBuild("No output artifacts expected for the job with ID: " + myJobID, build));
        } else {
          for (String path : getArtifactPaths(params)) {
            final Artifact artifact = getCorrespondingArtifact(path, outputArtifacts);
            final S3ArtifactLocation s3Location = artifact.getLocation().getS3Location();
            final File buildArtifact = FileUtil.resolvePath(build.getCheckoutDirectory(), path);

            build.getBuildLogger().message("Uploading job output artifact " + s3Location.getObjectKey() + " from " + buildArtifact.getAbsolutePath());
            getArtifactS3Client(jobData.getArtifactCredentials(), params)
              .putObject(new PutObjectRequest(s3Location.getBucketName(), s3Location.getObjectKey(), buildArtifact)
                .withSSEAwsKeyManagementParams(getSSEAwsKeyManagementParams(jobData.getEncryptionKey())));
          }

          publishJobSuccess(build);
        }
      }

    } catch (Throwable e) {
      failOnException(build, e);
    }
  }

  @NotNull
  private static SSEAwsKeyManagementParams getSSEAwsKeyManagementParams(@Nullable EncryptionKey encryptionKey) {
    return encryptionKey == null || encryptionKey.getId() == null || encryptionKey.getType() == null || !EncryptionKeyType.KMS.toString().equals(encryptionKey.getType())
      ? new SSEAwsKeyManagementParams() : new SSEAwsKeyManagementParams(encryptionKey.getId());
  }

  private void publishJobSuccess(@NotNull AgentRunningBuild build) {
    createAWSClients(build.getSharedConfigParameters()).createCodePipeLineClient().putJobSuccessResult(
      new PutJobSuccessResultRequest().withJobId(myJobID).withExecutionDetails(
        new ExecutionDetails().withExternalExecutionId(String.valueOf(build.getBuildId())).withSummary("Build successfully finished")
      )
    );
  }

  private void publishJobFailure(@NotNull AgentRunningBuild build, @NotNull String message) {
    try {
      createAWSClients(build.getSharedConfigParameters()).createCodePipeLineClient().putJobFailureResult(
        new PutJobFailureResultRequest().withJobId(myJobID).withFailureDetails(
          new FailureDetails()
            .withExternalExecutionId(String.valueOf(build.getBuildId()))
            .withType(FailureType.JobFailed)
            .withMessage(message)
        )
      );
    } catch (Throwable e) {
      LOG.error(msgForBuild(e.getMessage(), build), e);
      build.getBuildLogger().exception(e);
    } finally {
      myJobID = null;
    }
  }

  private void failOnException(@NotNull AgentRunningBuild build, @NotNull Throwable cause) {
    final AWSException e = new AWSException(cause);

    LOG.error(msgForBuild(e.getMessage(), build));
    build.getBuildLogger().error(e.getMessage());

    final String details = e.getDetails();
    if (StringUtil.isNotEmpty(details)) {
      LOG.error(details);
      build.getBuildLogger().error(details);
    }

    LOG.error(e);

    build.getBuildLogger().logBuildProblem(createBuildProblem(build, e));

    publishJobFailure(build, e.getMessage());
  }

  @NotNull
  private BuildProblemData createBuildProblem(@NotNull AgentRunningBuild build, @NotNull AWSException e) {
    return BuildProblemData.createBuildProblem(
      calculateIdentity(build, e.getIdentity(), e.getMessage()),
      e.getType(),
      removePaths(e.getMessage(), build));
  }

  @NotNull
  private String calculateIdentity(@NotNull AgentRunningBuild build, String... parts) {
    return String.valueOf(AWSCommonParams.calculateIdentity(build.getCheckoutDirectory().getAbsolutePath(), build.getSharedConfigParameters(), parts));
  }


  @Nullable
  private String removePaths(@Nullable String message, @NotNull AgentRunningBuild build) {
    if (message == null) return null;
    final String checkoutDir = FileUtil.toSystemIndependentName(build.getCheckoutDirectory().getAbsolutePath());
    return FileUtil.toSystemIndependentName(message).replace(checkoutDir + "/", StringUtil.EMPTY).replace(checkoutDir, StringUtil.EMPTY);
  }

  @NotNull
  private String msgForBuild(@NotNull String msg, @NotNull AgentRunningBuild build) {
    return build + ":\n" + msg;
  }

  @NotNull
  private JobData getJobData(@NotNull Map<String, String> params) {
    return createAWSClients(params).createCodePipeLineClient().getJobDetails(new GetJobDetailsRequest().withJobId(myJobID)).getJobDetails().getData();
  }

  @NotNull
  private AmazonS3Client getArtifactS3Client(@NotNull AWSSessionCredentials artifactCredentials, @NotNull Map<String, String> params) {
    return AWSClients.fromBasicSessionCredentials(artifactCredentials.getAccessKeyId(), artifactCredentials.getSecretAccessKey(), artifactCredentials.getSessionToken(), AWSCommonParams.getRegionName(params)).createS3Client();
  }

  private void validateOutputArtifacts(@NotNull List<Artifact> jobOutputArtifacts, @NotNull List<String> buildArtifactPaths) {
    final int outputArtifactsSize = jobOutputArtifacts.size();
    final int artifactPathsSize = buildArtifactPaths.size();

    if (outputArtifactsSize != artifactPathsSize) {
      throw new IllegalArgumentException(CodePipelineConstants.ARTIFACT_PATHS_LABEL +
        " parameter doesn't match the corresponding AWS CodePipeline action Output artifacts setting: " +
        outputArtifactsSize + " " + pluralize("artifact", outputArtifactsSize) + " expected, " +
        artifactPathsSize + " artifact " + pluralize("path", artifactPathsSize) + " provided");
    }

    for (String path : buildArtifactPaths) {
      if (null == getCorrespondingArtifact(path, jobOutputArtifacts)) {
        throw new IllegalArgumentException("No matching AWS CodePipeline action Output artifact found for " + path +
          ". Each archive specified in the " + CodePipelineConstants.ARTIFACT_PATHS_LABEL + " must match an AWS CodePipeline action Output artifact by name");
      }
    }
  }

  @Nullable
  private Artifact getCorrespondingArtifact(@NotNull String artifactPath, @NotNull List<Artifact> jobOutputArtifacts) {
    final String name = FileUtil.getNameWithoutExtension(artifactPath);
    return CollectionsUtil.findFirst(jobOutputArtifacts, new Filter<Artifact>() {
      @Override
      public boolean accept(@NotNull Artifact data) {
        return name.equals(data.getName());
      }
    });
  }
}
