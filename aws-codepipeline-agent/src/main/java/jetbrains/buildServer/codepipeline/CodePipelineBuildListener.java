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

import com.amazonaws.services.codepipeline.AWSCodePipelineClient;
import com.amazonaws.services.codepipeline.model.*;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.amazon.AWSClients;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.util.amazon.S3Util;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.codepipeline.CodePipelineConstants.ARTIFACT_INPUT_FOLDER_CONFIG_PARAM;
import static jetbrains.buildServer.codepipeline.CodePipelineConstants.ARTIFACT_OUTPUT_FOLDER_CONFIG_PARAM;
import static jetbrains.buildServer.codepipeline.CodePipelineUtil.getJobId;

/**
 * @author vbedrosova
 */
public class CodePipelineBuildListener extends AgentLifeCycleAdapter {
  @NotNull
  private static final Logger LOG = Logger.getLogger(CodePipelineBuildListener.class);

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
    processJobOutput(build, buildStatus);
  }

  private void processJobInput(@NotNull final AgentRunningBuild build) {
    if (myJobInputProcessed) return;
    myJobInputProcessed = true;

    final Map<String, String> params = build.getSharedConfigParameters();
    myJobID = getJobId(params);
    if (myJobID == null) {
      LOG.debug(msgForBuild("No AWS CodePipeline job found for the build", build));
      return;
    }

    AWSCommonParams.withAWSClients(params, new AWSCommonParams.WithAWSClients<Void, RuntimeException>() {
      @Nullable
      @Override
      public Void run(@NotNull AWSClients clients) throws RuntimeException {
        AWSCodePipelineClient codePipelineClient = null;
        try {
          codePipelineClient = clients.createCodePipeLineClient();
          final JobData jobData = getJobData(codePipelineClient, params);

          final PipelineContext pipelineContext = jobData.getPipelineContext();
          build.getBuildLogger().message(
            "This build is a part of an AWS CodePipeline pipeline: " + pipelineContext.getPipelineName() +
              "\nLink: https://console.aws.amazon.com/codepipeline/home?region=" + params.get(AWSCommonParams.REGION_NAME_PARAM) + "#/view/" + pipelineContext.getPipelineName() +
              "\nStage: " + pipelineContext.getStage().getName() +
              "\nAction: " + pipelineContext.getAction().getName() +
              "\nJob ID: " + myJobID);

          final List<Artifact> inputArtifacts = jobData.getInputArtifacts();
          if (inputArtifacts.isEmpty()) {
            LOG.debug(msgForBuild("No input artifacts provided for the job with ID: " + myJobID, build));
          } else {

            final File inputFolder = new File(params.get(ARTIFACT_INPUT_FOLDER_CONFIG_PARAM));
            FileUtil.createDir(inputFolder);

            final Collection<Download> downloads = S3Util.withTransferManager(getArtifactS3Client(jobData.getArtifactCredentials(), params), new S3Util.WithTransferManager<Download, Throwable>() {
              @NotNull
              @Override
              public Collection<Download> run(@NotNull final TransferManager manager) throws Throwable {
                return CollectionsUtil.convertCollection(inputArtifacts, new Converter<Download, Artifact>() {
                  @Override
                  public Download createFrom(@NotNull Artifact artifact) {
                    final S3ArtifactLocation s3Location = artifact.getLocation().getS3Location();
                    final File destinationFile = getInputArtifactFile(inputFolder, s3Location.getObjectKey());

                    build.getBuildLogger().message("Downloading job input artifact " + s3Location.getObjectKey() + " to " + destinationFile.getAbsolutePath());
                    return manager.download(s3Location.getBucketName(), s3Location.getObjectKey(), destinationFile);
                  }
                });
              }
            });
            // for backward compatibility, TW-47902
            for (Download d : downloads) {
              makeArtifactCopy(inputFolder, getInputArtifactFile(inputFolder, d.getKey()), d.getKey(), build);
            }
            if (!jobData.getOutputArtifacts().isEmpty()) {
              FileUtil.createDir(new File(params.get(ARTIFACT_OUTPUT_FOLDER_CONFIG_PARAM)));
            }
          }
        } catch (Throwable e) {
          failOnException(codePipelineClient, build, e);
        }
        return null;
      }
    });
  }

  @NotNull
  private File getInputArtifactFile(@NotNull File inputFolder, @NotNull String s3ObjectKey) {
    return new File(inputFolder, new File(s3ObjectKey).getParentFile().getName() + CodePipelineUtil.getArchiveExtension(s3ObjectKey));
  }

  private void makeArtifactCopy(@NotNull File inputFolder, @NotNull File artifactFile, @NotNull String path, @NotNull AgentRunningBuild build) {
    final File dest = new File(inputFolder, path);
    FileUtil.createParentDirs(dest);
    try {
      FileUtil.copy(artifactFile, dest);
    } catch (IOException e) {
      LOG.error(msgForBuild("Failed to copy " + artifactFile + " to " + dest, build), e);
    }
  }

  private void processJobOutput(@NotNull final AgentRunningBuild build, @NotNull final BuildFinishedStatus buildStatus) {
    if (myJobID == null) return;

    AWSCommonParams.withAWSClients(build.getSharedConfigParameters(), new AWSCommonParams.WithAWSClients<Void, RuntimeException>() {
      @Nullable
      @Override
      public Void run(@NotNull AWSClients clients) throws RuntimeException {
        AWSCodePipelineClient codePipelineClient = null;
        try {
          codePipelineClient = clients.createCodePipeLineClient();
          if (build.isBuildFailingOnServer()) {
            publishJobFailure(codePipelineClient, build, "Build failed");
          } else if (BuildFinishedStatus.INTERRUPTED == buildStatus) {
            publishJobFailure(codePipelineClient, build, "Build interrupted");
          } else {
            final Map<String, String> params = build.getSharedConfigParameters();
            final JobData jobData = getJobData(codePipelineClient, params);

            final List<Artifact> outputArtifacts = jobData.getOutputArtifacts();
            if (outputArtifacts.isEmpty()) {
              LOG.debug(msgForBuild("No output artifacts expected for the job with ID: " + myJobID, build));
            } else {
              final File artifactOutputFolder = new File(params.get(ARTIFACT_OUTPUT_FOLDER_CONFIG_PARAM));

              S3Util.withTransferManager(getArtifactS3Client(jobData.getArtifactCredentials(), params), new S3Util.WithTransferManager<Upload, Throwable>() {
                @NotNull
                @Override
                public Collection<Upload> run(@NotNull final TransferManager manager) throws Throwable {
                  return CollectionsUtil.convertCollection(outputArtifacts, new Converter<Upload, Artifact>() {
                    @Override
                    public Upload createFrom(@NotNull Artifact artifact) {
                      final File buildArtifact = getBuildArtifact(artifact, jobData.getPipelineContext().getPipelineName(), artifactOutputFolder, build);
                      final S3ArtifactLocation s3Location = artifact.getLocation().getS3Location();

                      build.getBuildLogger().message("Uploading job output artifact " + s3Location.getObjectKey() + " from " + buildArtifact.getAbsolutePath());
                      return manager.upload(new PutObjectRequest(s3Location.getBucketName(), s3Location.getObjectKey(), buildArtifact)
                        .withSSEAwsKeyManagementParams(getSSEAwsKeyManagementParams(jobData.getEncryptionKey())));
                    }
                  });
                }
              });

              publishJobSuccess(codePipelineClient, build);
            }
          }
        } catch (Throwable e) {
          failOnException(codePipelineClient, build, e);
        }
        return null;
      }
    });
  }

  @NotNull
  private static SSEAwsKeyManagementParams getSSEAwsKeyManagementParams(@Nullable EncryptionKey encryptionKey) {
    return encryptionKey == null || encryptionKey.getId() == null || encryptionKey.getType() == null || !EncryptionKeyType.KMS.toString().equals(encryptionKey.getType())
      ? new SSEAwsKeyManagementParams() : new SSEAwsKeyManagementParams(encryptionKey.getId());
  }

  private void publishJobSuccess(@NotNull AWSCodePipelineClient codePipelineClient, @NotNull AgentRunningBuild build) {
    codePipelineClient.putJobSuccessResult(
      new PutJobSuccessResultRequest().withJobId(myJobID).withExecutionDetails(
        new ExecutionDetails().withExternalExecutionId(String.valueOf(build.getBuildId())).withSummary("Build successfully finished")
      )
    );
  }

  private void publishJobFailure(@NotNull AWSCodePipelineClient codePipelineClient, @NotNull AgentRunningBuild build, @NotNull String message) {
    try {
      codePipelineClient.putJobFailureResult(
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

  private void failOnException(@Nullable AWSCodePipelineClient codePipelineClient, @NotNull AgentRunningBuild build, @NotNull Throwable cause) {
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

    if (codePipelineClient != null) {
      publishJobFailure(codePipelineClient, build, e.getMessage());
    }
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
  private JobData getJobData(@NotNull AWSCodePipelineClient codePipelineClient, @NotNull Map<String, String> params) {
    return codePipelineClient.getJobDetails(new GetJobDetailsRequest().withJobId(myJobID)).getJobDetails().getData();
  }

  @NotNull
  private AmazonS3Client getArtifactS3Client(@NotNull AWSSessionCredentials artifactCredentials, @NotNull Map<String, String> params) {
    return AWSClients.fromBasicSessionCredentials(artifactCredentials.getAccessKeyId(), artifactCredentials.getSecretAccessKey(), artifactCredentials.getSessionToken(), AWSCommonParams.getRegionName(params)).createS3Client();
  }

  @NotNull
  private File getBuildArtifact(@NotNull final Artifact artifact, @NotNull final String pipelineName, @NotNull final File artifactFolder, @NotNull AgentRunningBuild build) {
    final File zip = new File(artifactFolder, artifact.getName() + ".zip");
    if (zip.isFile()) return zip;

    final File tar = new File(artifactFolder, artifact.getName() + ".tar");
    if (tar.exists()) return tar;

    final File tarGz = new File(artifactFolder, artifact.getName() + ".tar.gz");
    if (tarGz.exists()) return tarGz;

    final File tgz = new File(artifactFolder, artifact.getName() + ".tgz");
    if (tgz.exists()) return tgz;

    final File parent = new File(artifactFolder, pipelineName + "/" + artifact.getName());
    if (parent.isDirectory()) {
      final File[] files = parent.listFiles();
      if (files != null && files.length > 0) {
        if (files.length > 1) {
          build.getBuildLogger().warning("Multiple output artifacts detected in " + parent.getAbsolutePath() + ". Will publish only one of them");
        }
        return files[0];
      }
    }
    throw new IllegalStateException("No output artifact " + artifact.getName() + " (zip, tar, tar.gz) found in " + artifactFolder.getAbsolutePath() + " folder");
  }
}
