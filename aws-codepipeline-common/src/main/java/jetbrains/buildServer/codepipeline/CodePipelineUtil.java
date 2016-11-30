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

import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * @author vbedrosova
 */
public final class CodePipelineUtil {
  @NotNull
  public static String printStrings(@NotNull Collection<String> strings) {
    if (strings.isEmpty()) return StringUtil.EMPTY;

    final StringBuilder sb = new StringBuilder();
    for (String s : strings) sb.append(s).append("\n");
    return sb.toString();
  }

  @Nullable
  public static String getJobId(@NotNull Map<String, String> params) {
    return params.get(CodePipelineConstants.JOB_ID_CONFIG_PARAM);
  }

  @Nullable
  public static String getActionToken(@NotNull Map<String, String> params) {
    return params.get(CodePipelineConstants.ACTION_TOKEN_PARAM);
  }

  @NotNull
  public static String getArchiveExtension(@NotNull String path) {
    if (path.endsWith(".zip")) return ".zip";
    if (path.endsWith(".tar")) return ".tar";
    if (path.endsWith(".tar.gz")) return ".tar.gz";
    if (path.endsWith(".tgz")) return ".tgz";
    return StringUtil.EMPTY;
  }
}
