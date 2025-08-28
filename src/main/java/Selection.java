import java.nio.file.Path;

public class Selection {
    public static boolean shouldIgnore(Path path, Path rootPath, AnalyzerConfig config) {
        String name = path.getFileName().toString();
        String rel = Util.relativizeSafe(rootPath, path);

        boolean excludeMode = !config.getExcludeExtensions().isEmpty() || !config.getExcludeNamesOrPaths().isEmpty();
        if (excludeMode) {
            if (Util.matchesNameOrPath(rel, name, config.getExcludeNamesOrPaths())) return true;
            String ext = Util.extensionOf(name);
            if (!ext.isEmpty() && config.getExcludeExtensions().contains(ext)) return true;
            return false;
        }
        return false;
    }

    public static boolean shouldIncludeFile(Path file, Path rootPath, AnalyzerConfig config) {
        String name = file.getFileName().toString();
        String rel = Util.relativizeSafe(rootPath, file);

        boolean excludeMode = !config.getExcludeExtensions().isEmpty() || !config.getExcludeNamesOrPaths().isEmpty();
        if (excludeMode) return true;

        boolean hasInclude = !config.getIncludeExtensions().isEmpty() || !config.getIncludeNamesOrPaths().isEmpty();
        if (!hasInclude) return true;

        if (Util.matchesNameOrPath(rel, name, config.getIncludeNamesOrPaths())) return true;
        String ext = Util.extensionOf(name);
        return !ext.isEmpty() && config.getIncludeExtensions().contains(ext);
    }
}

