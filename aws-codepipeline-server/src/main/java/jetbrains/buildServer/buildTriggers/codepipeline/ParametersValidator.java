

package jetbrains.buildServer.buildTriggers.codepipeline;

import jetbrains.buildServer.codepipeline.CodePipelineConstants;
import jetbrains.buildServer.codepipeline.CodePipelineUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author vbedrosova
 */
public class ParametersValidator {
  @NotNull
  public static Map<String, String> validateSettings(@NotNull Map<String, String> params, boolean acceptReferences) {
    final Map<String, String> invalids = new HashMap<String, String>(AWSCommonParams.validate(params, acceptReferences));

    if (StringUtil.isEmptyOrSpaces(CodePipelineUtil.getActionToken(params))) {
      invalids.put(CodePipelineConstants.ACTION_TOKEN_PARAM, CodePipelineConstants.ACTION_TOKEN_LABEL + " parameter must not be empty");
    }
    return invalids;
  }
}