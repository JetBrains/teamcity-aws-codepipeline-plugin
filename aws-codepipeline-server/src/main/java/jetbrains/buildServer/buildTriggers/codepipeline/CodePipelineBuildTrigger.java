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

package jetbrains.buildServer.buildTriggers.codepipeline;

import com.amazonaws.services.codepipeline.AWSCodePipelineClient;
import com.amazonaws.services.codepipeline.model.*;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.*;
import jetbrains.buildServer.codepipeline.CodePipelineUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSException;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.codepipeline.CodePipelineConstants.*;

/**
 * @author vbedrosova
 */
public class CodePipelineBuildTrigger extends BuildTriggerService {
  @NotNull
  private static final Logger LOG = Logger.getInstance(Loggers.VCS_CATEGORY + CodePipelineBuildTrigger.class);

  @NotNull
  private final PluginDescriptor myPluginDescriptor;
  @NotNull
  private final AWSCommonParams myAWSCommonParams;
  @NotNull
  private final BuildCustomizerFactory myBuildCustomizerFactory;

  public CodePipelineBuildTrigger(@NotNull PluginDescriptor pluginDescriptor,
                                  @NotNull AWSCommonParams awsCommonParams,
                                  @NotNull BuildCustomizerFactory buildCustomizerFactory) {
    myPluginDescriptor = pluginDescriptor;
    myAWSCommonParams = awsCommonParams;
    myBuildCustomizerFactory = buildCustomizerFactory;
  }

  @NotNull
  @Override
  public String getName() {
    return TRIGGER_NAME;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return TRIGGER_DISPLAY_NAME;
  }

  @NotNull
  @Override
  public String describeTrigger(@NotNull BuildTriggerDescriptor trigger) {
    return TRIGGER_DESCR;
  }

  @NotNull
  @Override
  public BuildTriggeringPolicy getBuildTriggeringPolicy() {
    return new PolledBuildTrigger() {
      @Override
      public void triggerBuild(@NotNull PolledTriggerContext context) throws BuildTriggerException {
        try {
          final AWSCodePipelineClient codePipelineClient =
            AWSCommonParams.createAWSClients(validateParams(context.getTriggerDescriptor().getProperties())).createCodePipeLineClient();

          final PollForJobsRequest request = new PollForJobsRequest()
            .withActionTypeId(
              new ActionTypeId()
                .withCategory(ActionCategory.Build)
                .withOwner(ActionOwner.Custom)
                .withProvider(TEAMCITY_ACTION_PROVIDER)
                .withVersion(getActionTypeVersion(codePipelineClient)))
            .withQueryParam(CollectionsUtil.<String>asMap(
//              TEAMCITY_SERVER_URL_CONFIG_PROPERTY, myServerSettings.getRootUrl(),
//              BUILD_TYPE_ID_CONFIG_PROPERTY, context.getBuildType().getExternalId(),
              ACTION_TOKEN_CONFIG_PROPERTY, CodePipelineUtil.getActionToken(context.getTriggerDescriptor().getProperties())))
            .withMaxBatchSize(1);

          final List<Job> jobs = codePipelineClient.pollForJobs(request).getJobs();

          if (jobs.size() > 0) {
            if (jobs.size() > 1) {
              LOG.warn(msgForBt("Received " + jobs.size() + ", but only one was expected. Will process only the first job", context.getBuildType()));
            }

            final Job job = jobs.get(0);
            LOG.info(msgForBt("Received job request with ID: " + job.getId() + " and nonce: " + job.getNonce(), context.getBuildType()));

            final AcknowledgeJobRequest acknowledgeJobRequest = new AcknowledgeJobRequest()
              .withJobId(job.getId())
              .withNonce(job.getNonce());

            final String jobStatus = codePipelineClient.acknowledgeJob(acknowledgeJobRequest).getStatus();
            if (jobStatus.equals(JobStatus.InProgress.name())) {

              final BuildCustomizer buildCustomizer = myBuildCustomizerFactory.createBuildCustomizer(context.getBuildType(), null);
              buildCustomizer.setParameters(getCustomBuildParameters(job, context.getTriggerDescriptor()));

              final BuildPromotion promotion = buildCustomizer.createPromotion();
              promotion.addToQueue(TRIGGER_DISPLAY_NAME + " job with ID: " + job.getId());

              LOG.info(msgForBt("Acknowledged job with ID: " + job.getId()+ " and nonce: " + job.getNonce() + ", created build promotion " + promotion.getId(), context.getBuildType()));

            } else {
              LOG.warn(msgForBt("Job ignored with ID: " + job.getId()+ " and nonce: " + job.getNonce() + " because job status is " + jobStatus, context.getBuildType()));
            }
          } else {
            LOG.debug(msgForBt("No jobs found", context.getBuildType()));
          }
        } catch (Throwable e) {
          if (e instanceof BuildTriggerException) throw (BuildTriggerException) e;

          final AWSException awse = new AWSException(e);
          final String details = awse.getDetails();
          if (StringUtil.isNotEmpty(details)) LOG.error(details);
          LOG.error(awse);

          throw new BuildTriggerException(e.getMessage(), awse);
        }
      }

      @NotNull
      private String getActionTypeVersion(@NotNull AWSCodePipelineClient codePipelineClient) {
        final ActionType teamCityActionType = CollectionsUtil.findFirst(codePipelineClient.listActionTypes(new ListActionTypesRequest().withActionOwnerFilter(ActionOwner.Custom)).getActionTypes(), new Filter<ActionType>() {
          @Override
          public boolean accept(@NotNull ActionType data) {
            return TEAMCITY_ACTION_PROVIDER.equals(data.getId().getProvider());
          }
        });
        return teamCityActionType == null ? DEFAULT_ACTION_VERSION : teamCityActionType.getId().getVersion();
      }

      @NotNull
      private Map<String, String> getCustomBuildParameters(@NotNull Job job, @NotNull BuildTriggerDescriptor triggerDescriptor) {
        final HashMap<String, String> params = new HashMap<>(triggerDescriptor.getProperties());
        params.put(JOB_ID, job.getId());
        return params;
      }

      @NotNull
      private String msgForBt(@NotNull String msg, @NotNull SBuildType bt) {
        return bt + ":" + msg;
      }

      @NotNull
      private Map<String, String> validateParams(@NotNull Map<String, String> params) throws BuildTriggerException {
        final Map<String, String> invalids = ParametersValidator.validateSettings(params, false);
        if (invalids.isEmpty()) return params;
        throw new BuildTriggerException(CodePipelineUtil.printStrings(invalids.values()), null);
      }
    };
  }

  @NotNull
  @Override
  public PropertiesProcessor getTriggerPropertiesProcessor() {
    return new PropertiesProcessor() {
      @Override
      public Collection<InvalidProperty> process(Map<String, String> properties) {
        return CollectionsUtil.convertCollection(ParametersValidator.validateSettings(properties, false).entrySet(), new Converter<InvalidProperty, Map.Entry<String, String>>() {
          @Override
          public InvalidProperty createFrom(@NotNull Map.Entry<String, String> source) {
            return new InvalidProperty(source.getKey(), source.getValue());
          }
        });
      }
    };
  }

  @NotNull
  @Override
  public String getEditParametersUrl() {
    return myPluginDescriptor.getPluginResourcesPath(EDIT_PARAMS_JSP);
  }

  @NotNull
  @Override
  public Map<String, String> getDefaultTriggerProperties() {
    return myAWSCommonParams.getDefaults();
  }
}
