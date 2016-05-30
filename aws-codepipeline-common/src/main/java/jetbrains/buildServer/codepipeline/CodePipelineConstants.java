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

/**
 * @author vbedrosova
 */
public interface CodePipelineConstants {
  String TRIGGER_NAME = "aws.codePipeline";
  String TRIGGER_DISPLAY_NAME = "AWS CodePipeline Action";
  String TRIGGER_DESCR = "Poll AWS CodePipeline for job requests";

  String TEAMCITY_ACTION_PROVIDER = "TeamCity";
  String TEAMCITY_SERVER_URL_CONFIG_PROPERTY = "TeamCity Server URL";
  String BUILD_TYPE_ID_CONFIG_PROPERTY = "Build Type Id";
  String ACTION_TOKEN_CONFIG_PROPERTY = "Action Token";
  String DEFAULT_ACTION_VERSION = "1";

  String JOB_ID = "codepipeline.job.id";

  String EDIT_PARAMS_JSP = "editCodePipelineTrigger.jsp";

  String ARTIFACT_PATHS_PARAM = "codepipeline_artifact_paths";
  String ARTIFACT_PATHS_LABEL = "Action output artifacts";
  String ACTION_TOKEN_PARAM = "codepipeline_action_tocken";
  String ACTION_TOKEN_LABEL = "Action Token";

  String MULTILINE_SPLIT_REGEX = " *[,\n\r] *";
}