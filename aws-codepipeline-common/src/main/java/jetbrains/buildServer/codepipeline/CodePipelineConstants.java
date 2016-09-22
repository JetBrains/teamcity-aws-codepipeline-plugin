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

  String TEAMCITY_BUILD_TEMP_DIR = "%system.teamcity.build.tempDir%";

  String TEAMCITY_ACTION_PROVIDER = "TeamCity";
  String ACTION_TOKEN_CONFIG_PROPERTY = "ActionID";

  String JOB_ID_CONFIG_PARAM = "codepipeline.job.id";

  String EDIT_PARAMS_JSP = "editCodePipelineTrigger.jsp";

  String ACTION_TOKEN_PARAM = "codepipeline_action_tocken";
  String ACTION_TOKEN_LABEL = "ActionID";

  String POLL_INTERVAL_CONFIG_PARAM = "codepipeline.poll.interval";

  String ARTIFACT_INPUT_FOLDER = TEAMCITY_BUILD_TEMP_DIR + "/CodePipeline/input";
  String ARTIFACT_OUTPUT_FOLDER = TEAMCITY_BUILD_TEMP_DIR + "/CodePipeline/output";

  String ARTIFACT_INPUT_FOLDER_CONFIG_PARAM = "codepipeline.artifact.input.folder";
  String ARTIFACT_OUTPUT_FOLDER_CONFIG_PARAM = "codepipeline.artifact.output.folder";
}