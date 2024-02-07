

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