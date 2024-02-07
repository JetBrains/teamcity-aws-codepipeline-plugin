

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