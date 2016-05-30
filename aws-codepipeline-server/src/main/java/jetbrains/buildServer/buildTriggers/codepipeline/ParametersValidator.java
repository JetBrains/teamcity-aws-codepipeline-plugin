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

import jetbrains.buildServer.codepipeline.CodePipelineConstants;
import jetbrains.buildServer.codepipeline.CodePipelineUtil;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.amazon.AWSCommonParams;
import jetbrains.buildServer.util.amazon.AWSUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author vbedrosova
 */
public class ParametersValidator {
  @NotNull
  public static Map<String, String> validateSettings(@NotNull Map<String, String> params, boolean acceptReferences) {
    final Map<String, String> invalids = new HashMap<String, String>(AWSCommonParams.validate(params, acceptReferences));

    if (StringUtil.isEmptyOrSpaces(CodePipelineUtil.getActionToken(params))) {
      invalids.put(CodePipelineConstants.ACTION_TOKEN_PARAM, CodePipelineConstants.ACTION_TOKEN_LABEL + " parameter mustn't be empty");
    }

    final String artifactPaths = params.get(CodePipelineConstants.ARTIFACT_PATHS_PARAM);
    if (StringUtil.isNotEmpty(artifactPaths)) {
      if (!ReferencesResolverUtil.containsReference(artifactPaths) || !acceptReferences) {
        final List<String> paths = CodePipelineUtil.getArtifactPaths(artifactPaths);
        if (params.size() == 0 || paths.size() > 5) {
          invalids.put(CodePipelineConstants.ARTIFACT_PATHS_PARAM, CodePipelineConstants.ARTIFACT_PATHS_LABEL + " parameter must contain 0 to 5 artifact paths (limited by AWS CodePipeline)");
        } else {
          for (String path : paths) {
            if (AWSUtil.getBundleType(path) == null) {
              invalids.put(CodePipelineConstants.ARTIFACT_PATHS_PARAM, path + " provides invalid bundle type, supported bundle types are .zip, .tar and .tar.gz");
              break;
            }
          }
        }
      }
    }

    return invalids;
  }
}
