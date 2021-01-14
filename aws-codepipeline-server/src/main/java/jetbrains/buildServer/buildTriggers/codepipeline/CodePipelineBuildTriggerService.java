/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.BuildTriggerService;
import jetbrains.buildServer.buildTriggers.BuildTriggeringPolicy;
import jetbrains.buildServer.buildTriggers.PolledBuildTrigger;
import jetbrains.buildServer.buildTriggers.async.AsyncPolledBuildTriggerFactory;
import jetbrains.buildServer.codepipeline.CodePipelineUtil;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

import static jetbrains.buildServer.codepipeline.CodePipelineConstants.*;

/**
 * @author vbedrosova
 */
public class CodePipelineBuildTriggerService extends BuildTriggerService {

  @NotNull
  private final PluginDescriptor myPluginDescriptor;
  @NotNull
  private final ServerSettings myServerSettings;
  @NotNull
  private final PolledBuildTrigger myPolicy;

  public CodePipelineBuildTriggerService(@NotNull PluginDescriptor pluginDescriptor,
                                         @NotNull ServerSettings serverSettings,
                                         @NotNull CodePipelineAsyncPolledBuildTrigger trigger,
                                         @NotNull AsyncPolledBuildTriggerFactory triggerFactory) {
    myPluginDescriptor = pluginDescriptor;
    myServerSettings = serverSettings;
    myPolicy = triggerFactory.createBuildTrigger(trigger, CodePipelineAsyncPolledBuildTrigger.LOG);
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
    return String.format(
      TRIGGER_DESCR + " with %s " + ACTION_TOKEN_CONFIG_PROPERTY,
      CodePipelineUtil.getActionToken(trigger.getProperties())
    );
  }

  @NotNull
  @Override
  public BuildTriggeringPolicy getBuildTriggeringPolicy() {
    return myPolicy;
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
    return AWSCommonParams.getDefaults(myServerSettings.getServerUUID());
  }
}
