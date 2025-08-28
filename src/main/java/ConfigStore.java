import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

public class ConfigStore {

    public static Path getStoreDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path base;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                base = Paths.get(appData);
            } else {
                base = Paths.get(System.getProperty("user.home"), "AppData", "Roaming");
            }
            return base.resolve("ctxgen");
        } else if (os.contains("mac")) {
            base = Paths.get(System.getProperty("user.home"), "Library", "Application Support");
            return base.resolve("ctxgen");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            if (xdg != null && !xdg.isBlank()) {
                base = Paths.get(xdg);
            } else {
                base = Paths.get(System.getProperty("user.home"), ".config");
            }
            return base.resolve("ctxgen");
        }
    }

    public static Path pathForName(String name) {
        if (!name.endsWith(".yaml") && !name.endsWith(".yml")) {
            name = name + ".yaml";
        }
        return getStoreDir().resolve(name);
    }

    public static boolean saveFrom(Path sourceConfig, String name) {
        try {
            if (!Files.exists(sourceConfig)) {
                System.err.println("Config file not found: " + sourceConfig);
                return false;
            }
            Path dir = getStoreDir();
            Files.createDirectories(dir);
            Path target = pathForName(name);
            Files.copy(sourceConfig, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Saved profile '" + name + "' at: " + target);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to save profile: " + e.getMessage());
            return false;
        }
    }

    public static AnalyzerConfig loadNamed(String name) {
        Path path = pathForName(name);
        if (!Files.exists(path)) {
            System.err.println("Profile not found: " + path);
            return null;
        }
        try (InputStream is = Files.newInputStream(path)) {
            LoaderOptions loaderOptions = new LoaderOptions();
            Constructor constructor = new Constructor(AnalyzerConfig.class, loaderOptions);
            Yaml yaml = new Yaml(constructor);
            AnalyzerConfig cfg = yaml.load(is);
            if (cfg == null) cfg = new AnalyzerConfig();
            // normalize nullable fields
            if (cfg.getIncludeExtensions() == null) cfg.setIncludeExtensions(new java.util.HashSet<>());
            if (cfg.getIncludeNamesOrPaths() == null) cfg.setIncludeNamesOrPaths(new java.util.HashSet<>());
            if (cfg.getExcludeExtensions() == null) cfg.setExcludeExtensions(new java.util.HashSet<>());
            if (cfg.getExcludeNamesOrPaths() == null) cfg.setExcludeNamesOrPaths(new java.util.HashSet<>());
            if (cfg.getExcludeNames() != null && !cfg.getExcludeNames().isEmpty()) {
                cfg.getExcludeNamesOrPaths().addAll(cfg.getExcludeNames());
            }
            cfg.getExcludeNamesOrPaths().add("project_structure.md");
            return cfg;
        } catch (Exception e) {
            System.err.println("Failed to load profile '" + name + "': " + e.getMessage());
            return null;
        }
    }

    public static java.util.List<String> listProfiles() {
        Path dir = getStoreDir();
        java.util.List<String> names = new java.util.ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return names;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.y{a,}ml")) {
            // Note: glob with {} may not work on all FS providers; fallback below
            for (Path p : ds) {
                String n = p.getFileName().toString();
                if (n.endsWith(".yaml")) n = n.substring(0, n.length() - 5);
                else if (n.endsWith(".yml")) n = n.substring(0, n.length() - 4);
                names.add(n);
            }
        } catch (IOException e) {
            // Fallback: manual scan
            try (DirectoryStream<Path> all = Files.newDirectoryStream(dir)) {
                for (Path p : all) {
                    String n = p.getFileName().toString();
                    if (n.endsWith(".yaml")) names.add(n.substring(0, n.length() - 5));
                    else if (n.endsWith(".yml")) names.add(n.substring(0, n.length() - 4));
                }
            } catch (IOException ignored) {}
        }
        java.util.Collections.sort(names);
        return names;
        }

    public static boolean deleteProfile(String name) {
        Path path = pathForName(name);
        try {
            if (Files.exists(path)) {
                Files.delete(path);
                System.out.println("Deleted profile: " + name);
                return true;
            } else {
                System.err.println("Profile not found: " + name);
                return false;
            }
        } catch (IOException e) {
            System.err.println("Failed to delete profile: " + e.getMessage());
            return false;
        }
    }
}
