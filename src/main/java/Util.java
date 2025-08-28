import java.nio.file.Path;

public class Util {
    public static boolean matchesNameOrPath(String relativePath, String name, java.util.Set<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return false;
        if (patterns.contains(name)) return true;
        String rel = relativePath.replace("\\", "/");
        if (patterns.contains(rel)) return true;
        for (String p : patterns) {
            if (p == null || p.isEmpty()) continue;
            String norm = p.replace("\\", "/");
            if (rel.equals(norm)) return true;
            if (rel.startsWith(norm.endsWith("/") ? norm : norm + "/")) return true;
        }
        return false;
    }

    public static String relativizeSafe(Path root, Path path) {
        try {
            return root.relativize(path.toAbsolutePath().normalize()).toString().replace("\\", "/");
        } catch (Exception e) {
            return path.getFileName().toString();
        }
    }

    public static String extensionOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx).toLowerCase() : "";
    }
}

