import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.nio.file.attribute.PosixFilePermission;

public class ProjectAnalyzer {

    private static final String OUTPUT_FILENAME = "project_structure.md";
    private static final String CONFIG_FILENAME = "context_config.yaml";

    public static class Config {
        // Include lists
        private Set<String> includeExtensions = new HashSet<>();
        private Set<String> includeNamesOrPaths = new HashSet<>();

        // Exclude lists
        private Set<String> excludeExtensions = new HashSet<>();
        private Set<String> excludeNamesOrPaths = new HashSet<>();

        // Backward-compat: old key "excludeNames" (mapped to excludeNamesOrPaths)
        private Set<String> excludeNames = new HashSet<>();

        public Config() {}

        public Set<String> getIncludeExtensions() { return includeExtensions; }
        public void setIncludeExtensions(Set<String> includeExtensions) { this.includeExtensions = includeExtensions != null ? includeExtensions : new HashSet<>(); }

        public Set<String> getIncludeNamesOrPaths() { return includeNamesOrPaths; }
        public void setIncludeNamesOrPaths(Set<String> includeNamesOrPaths) { this.includeNamesOrPaths = includeNamesOrPaths != null ? includeNamesOrPaths : new HashSet<>(); }

        public Set<String> getExcludeExtensions() { return excludeExtensions; }
        public void setExcludeExtensions(Set<String> excludeExtensions) { this.excludeExtensions = excludeExtensions != null ? excludeExtensions : new HashSet<>(); }

        public Set<String> getExcludeNamesOrPaths() { return excludeNamesOrPaths; }
        public void setExcludeNamesOrPaths(Set<String> excludeNamesOrPaths) { this.excludeNamesOrPaths = excludeNamesOrPaths != null ? excludeNamesOrPaths : new HashSet<>(); }

        // Backward-compat accessors
        public Set<String> getExcludeNames() { return excludeNames; }
        public void setExcludeNames(Set<String> excludeNames) { this.excludeNames = excludeNames != null ? excludeNames : new HashSet<>(); }

        @Override
        public String toString() {
            return "Config{" +
                    "includeExtensions=" + includeExtensions +
                    ", includeNamesOrPaths=" + includeNamesOrPaths +
                    ", excludeExtensions=" + excludeExtensions +
                    ", excludeNamesOrPaths=" + excludeNamesOrPaths +
                    '}';
        }
    }

    // New default config generator with 4 lists and precedence rules
    private static void createDefaultConfigV2() {
        try (FileWriter writer = new FileWriter(CONFIG_FILENAME)) {
            writer.write("# Include lists are ignored if any exclude list is non-empty.\n");
            writer.write("# Paths are relative to analysis root and use forward slashes.\n\n");

            writer.write("# Files to include by extension (with dot), empty = all\n");
            writer.write("includeExtensions:\n");
            writer.write("  # - \".java\"\n");

            writer.write("\n# Files or directories to include by name or relative path\n");
            writer.write("includeNamesOrPaths:\n");
            writer.write("  # - \"src/main/java\"\n");

            writer.write("\n# Files to exclude by extension (with dot)\n");
            writer.write("excludeExtensions:\n");
            writer.write("  # - \".class\"\n");

            writer.write("\n# Files or directories to exclude by name or relative path\n");
            writer.write("excludeNamesOrPaths:\n");
            writer.write("  - \".git\"\n");
            writer.write("  - \".idea\"\n");
            writer.write("  - \"context_config.yaml\"\n");
            writer.write("  - \"project_structure.md\"\n");
            writer.write("  - \"README.md\"\n");
            writer.write("  - \"target\"\n");
            writer.write("  - \"out\"\n");
        } catch (IOException e) {
            System.err.println("Error creating configuration file: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--config".equalsIgnoreCase(args[0])) {
            createDefaultConfigV2();
            System.out.println("Configuration file '" + CONFIG_FILENAME + "' created.");
            return;
        }

        if (args.length > 0 && "--install".equalsIgnoreCase(args[0])) {
            boolean ok = performInstall();
            if (ok) {
                System.out.println("Installed launcher 'ctxgen'. Try: ctxgen --config");
            } else {
                System.err.println("Installation was not completed. See messages above for next steps.");
            }
            return;
        }

        Config config = loadConfig();

        String path = ".";
        if (args.length > 0) {
            path = args[0];
        }
        Path projectRoot = Paths.get(path).toAbsolutePath().normalize();

        if (!Files.exists(projectRoot) || !Files.isDirectory(projectRoot)) {
            System.err.println("Error: Provided path '" + projectRoot.toAbsolutePath() + "' is not a valid directory.");
            System.exit(1);
        }

        Path outputFile = projectRoot.resolve(OUTPUT_FILENAME);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            writer.write("# Project structure overview\n");

            writer.write("\n<!-- Configuration used for this analysis:\n");
            writer.write("Include Extensions: " + config.getIncludeExtensions() + "\n");
            writer.write("Include Names/Paths: " + config.getIncludeNamesOrPaths() + "\n");
            writer.write("Exclude Extensions: " + config.getExcludeExtensions() + "\n");
            writer.write("Exclude Names/Paths: " + config.getExcludeNamesOrPaths() + "\n");
            writer.write("-->\n\n");

            writer.write("```\n");
            generateTree(writer, projectRoot, projectRoot, 0, config);
            writer.write("```\n");

            processFiles(writer, projectRoot, projectRoot, config);

            System.out.println("Analysis complete. Output written to: " + outputFile.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("An error occurred during analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean performInstall() {
        try {
            Path jarPath = detectExecutableJar();
            if (jarPath == null || !Files.exists(jarPath)) {
                System.err.println("Could not determine the executable JAR. Build the project first (mvn package) or run from JAR.");
                return false;
            }

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                return installOnWindows(jarPath);
            } else {
                return installOnUnix(jarPath);
            }
        } catch (Exception e) {
            System.err.println("Install failed: " + e.getMessage());
            return false;
        }
    }

    private static Path detectExecutableJar() {
        try {
            var codeSource = ProjectAnalyzer.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path loc = Paths.get(codeSource.getLocation().toURI());
                if (loc.toString().toLowerCase().endsWith(".jar")) {
                    return loc.toAbsolutePath().normalize();
                }
            }
        } catch (Exception ignored) { }

        // Fallback: look for a built jar in target/
        try {
            Path target = Paths.get("target");
            if (Files.isDirectory(target)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(target, "*.jar")) {
                    Path first = null;
                    for (Path p : ds) {
                        if (p.getFileName().toString().startsWith("context-")) return p.toAbsolutePath().normalize();
                        if (first == null) first = p;
                    }
                    if (first != null) return first.toAbsolutePath().normalize();
                }
            }
        } catch (IOException ignored) { }
        return null;
    }

    private static boolean installOnWindows(Path jarPath) {
        String wrapper = "@echo off\r\n" +
                "setlocal\r\n" +
                "java -jar \"" + jarPath.toString() + "\" %*\r\n" +
                "endlocal\r\n";

        String ps1 = "$jar = \"" + jarPath.toString().replace("\\", "/") + "\"\r\n" +
                "& java -jar \"$jar\" @args\r\n";

        // Try to find a writable directory already on PATH
        String pathEnv = System.getenv("PATH");
        Path chosenDir = null;
        if (pathEnv != null) {
            String[] dirs = pathEnv.split(Pattern.quote(File.pathSeparator));
            for (String d : dirs) {
                try {
                    if (d == null || d.isBlank()) continue;
                    Path dir = Paths.get(d.trim());
                    if (!Files.isDirectory(dir)) continue;
                    // test write permission by creating temp file
                    Path test = dir.resolve(".ctxgen_write_test.tmp");
                    try {
                        Files.writeString(test, "ok", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        Files.deleteIfExists(test);
                        chosenDir = dir;
                        break;
                    } catch (Exception ignored) { }
                } catch (Exception ignored) { }
            }
        }

        Path installDir;
        boolean onPath;
        if (chosenDir != null) {
            installDir = chosenDir;
            onPath = true;
        } else {
            // Fallback to %USERPROFILE%\bin
            installDir = Paths.get(System.getProperty("user.home"), "bin");
            try { Files.createDirectories(installDir); } catch (IOException ignored) {}
            onPath = pathEnv != null && Arrays.stream(pathEnv.split(Pattern.quote(File.pathSeparator)))
                    .map(String::trim).anyMatch(p -> p.equalsIgnoreCase(installDir.toString()));
        }

        Path cmd = installDir.resolve("ctxgen.cmd");
        Path ps1Path = installDir.resolve("ctxgen.ps1");
        try {
            Files.writeString(cmd, wrapper, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(ps1Path, ps1, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to write wrapper at: " + cmd + ". " + e.getMessage());
            return false;
        }

        if (!onPath) {
            // Best-effort append to user PATH via setx
            try {
                String newPath = (pathEnv == null || pathEnv.isBlank()) ? installDir.toString() : pathEnv + File.pathSeparator + installDir;
                new ProcessBuilder("cmd", "/c", "setx", "PATH", newPath).inheritIO().start().waitFor();
                System.out.println("Added to PATH for future sessions: " + installDir);
                System.out.println("Restart terminal or log out/in for changes to take effect.");
            } catch (Exception e) {
                System.out.println("Place added: " + installDir + ". If not on PATH, add it manually in Environment Variables.");
            }
        }

        System.out.println("Windows install complete: " + cmd + " and " + ps1Path);
        System.out.println("PowerShell note: you may need to set ExecutionPolicy to RemoteSigned for running local scripts.");
        return true;
    }

    private static boolean installOnUnix(Path jarPath) {
        String script = "#!/bin/sh\n" +
                "exec java -jar \"" + jarPath.toString() + "\" \"$@\"\n";

        // First try /usr/local/bin (requires sudo typically)
        Path target = Paths.get("/usr/local/bin/ctxgen");
        try {
            writeExecutable(target, script);
            System.out.println("Installed to /usr/local/bin. You can run: ctxgen --config");
            return true;
        } catch (Exception e) {
            // Fallback to user-local bin
            Path home = Paths.get(System.getProperty("user.home"));
            Path localBin = home.resolve(".local/bin");
            try { Files.createDirectories(localBin); } catch (IOException ignored) {}
            target = localBin.resolve("ctxgen");
            try {
                writeExecutable(target, script);
                System.out.println("Installed to " + target + ". Ensure '~/.local/bin' is in PATH.");
                return true;
            } catch (Exception e2) {
                // Final fallback: ~/bin
                Path bin = home.resolve("bin");
                try { Files.createDirectories(bin); } catch (IOException ignored) {}
                target = bin.resolve("ctxgen");
                try {
                    writeExecutable(target, script);
                    System.out.println("Installed to " + target + ". Add it to PATH to use 'ctxgen'.");
                    return true;
                } catch (Exception e3) {
                    System.err.println("Could not install wrapper: " + e3.getMessage());
                    return false;
                }
            }
        }
    }

    private static void writeExecutable(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            // Set executable bit on POSIX systems
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE
            );
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX (e.g., Windows) — ignore
        }
    }

    private static void createDefaultConfig() {
        org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
        options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);

        try (FileWriter writer = new FileWriter(CONFIG_FILENAME)) {
            writer.write("# File extensions to include in the analysis (including the dot)\n");
            writer.write("# An empty list means all files are included.\n");
            // Создаем пустой список для includeExtensions
            writer.write("includeExtensions: " + yaml.dump(new ArrayList<>()).trim() + "\n");

            writer.write("\n# File or directory names to ignore (including hidden items starting with '.')\n");
            List<String> defaultExcludes = Arrays.asList(
                    "- \".git\"",
                    "- \"context_config.yaml\"",
                    "- \".idea\"",
                    "- \"project_structure.md\"",
                    "- \"README.md\""
            );
            writer.write("excludeNames:\n");
            for (int i = 0; i < defaultExcludes.toArray().length; i++) {
                writer.write(defaultExcludes.get(i)+"\n");
            }
        } catch (IOException e) {
            System.err.println("Error creating configuration file: " + e.getMessage());
            System.exit(1);
        }
    }
    private static Config loadConfig() {
        Path configPath = Paths.get(CONFIG_FILENAME);
        if (!Files.exists(configPath)) {
            System.out.println("Configuration file '" + CONFIG_FILENAME + "' not found. Using default settings (all lists are empty).");
            Config defaultConfig = new Config();
            defaultConfig.getExcludeNames().add(OUTPUT_FILENAME);
            return defaultConfig;
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(Config.class, loaderOptions);
        Yaml yaml = new Yaml(constructor);

        try (InputStream inputStream = Files.newInputStream(configPath)) {
            Config config = yaml.load(inputStream);
            if (config == null) {
                System.out.println("Configuration file '" + CONFIG_FILENAME + "' is empty. Using default settings.");
                config = new Config();
            }
            System.out.println("Configuration loaded from '" + CONFIG_FILENAME + "'.");

            if (config.getIncludeExtensions() == null) config.setIncludeExtensions(new HashSet<>());
            if (config.getIncludeNamesOrPaths() == null) config.setIncludeNamesOrPaths(new HashSet<>());
            if (config.getExcludeExtensions() == null) config.setExcludeExtensions(new HashSet<>());
            if (config.getExcludeNamesOrPaths() == null) config.setExcludeNamesOrPaths(new HashSet<>());
            // Merge backward-compat key excludeNames -> excludeNamesOrPaths
            if (config.getExcludeNames() != null && !config.getExcludeNames().isEmpty()) {
                config.getExcludeNamesOrPaths().addAll(config.getExcludeNames());
            }
            config.getExcludeNamesOrPaths().add(OUTPUT_FILENAME);
            return config;
        } catch (Exception e) {
            System.err.println("Error reading/parsing configuration file: " + e.getMessage());
            System.err.println("Using default settings.");
            Config defaultConfig = new Config();
            defaultConfig.getExcludeNamesOrPaths().add(OUTPUT_FILENAME);
            return defaultConfig;
        }
    }

    private static void generateTree(BufferedWriter writer, Path currentPath, Path rootPath, int depth, Config config) throws IOException {
        try {
            var entries = Files.list(currentPath)
                    .filter(p -> !shouldIgnore(p, rootPath, config))
                    .sorted((p1, p2) -> {
                        if (Files.isDirectory(p1) && Files.isRegularFile(p2)) return -1;
                        if (Files.isRegularFile(p1) && Files.isDirectory(p2)) return 1;
                        return p1.getFileName().toString().compareTo(p2.getFileName().toString());
                    })
                    .toList();

            for (Path entry : entries) {
                String indent = "    ".repeat(depth);
                String name = entry.getFileName().toString();

                if (Files.isDirectory(entry)) {
                    writer.write(indent + "├── " + name + "/\n");
                    generateTree(writer, entry, rootPath, depth + 1, config);
                } else {
                    long size;
                    try {
                        size = Files.size(entry);
                    } catch (IOException e) {
                        size = 0;
                    }
                    writer.write(indent + "├── " + name + " [" + size + " chars]\n");
                }
            }
        } catch (IOException e) {
            String indent = "    ".repeat(depth);
            writer.write(indent + "├── [inaccessible directory]\n");
        }
    }

    private static void processFiles(BufferedWriter writer, Path currentPath, Path rootPath, Config config) throws IOException {
        try {
            var entries = Files.list(currentPath)
                    .filter(p -> !shouldIgnore(p, rootPath, config))
                    .sorted()
                    .toList();

            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    processFiles(writer, entry, rootPath, config);
                } else {
                    if (shouldIncludeFile(entry, rootPath, config)) {
                        appendFileContent(writer, entry, rootPath);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not access directory " + currentPath + ": " + e.getMessage());
        }
    }

    private static void appendFileContent(BufferedWriter writer, Path file, Path rootPath) throws IOException {
        try {
            String relativePath = rootPath.relativize(file).toString().replace("\\", "/");


            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                writer.write("\n**Path: `" + relativePath + "`**\n");
                writer.write("```" + detectCodeBlockType(file) + "\n");
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.write("\n");
                }
            }
            writer.write("```\n");
        } catch (IOException e) {

        }
    }

    private static String detectCodeBlockType(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".xml")) {
            return "xml";
        } else if (fileName.endsWith(".gradle") || fileName.endsWith(".kts")) {
            return "kotlin";
        } else if (fileName.endsWith(".java")) {
            return "java";
        } else if (fileName.endsWith(".kt")) {
            return "kotlin";
        } else if (fileName.endsWith(".md")) {
            return "markdown";
        } else if (fileName.endsWith(".js")) {
            return "javascript";
        } else if (fileName.endsWith(".php")) {
            return "php";
        }
        return "";
    }

    private static boolean shouldIgnore(Path path, Path rootPath, Config config) {
        String name = path.getFileName().toString();
        String rel = relativizeSafe(rootPath, path);

        // Do not auto-ignore hidden dot-files/directories; honor config only

        // Exclude lists take precedence (mutually exclusive with include lists)
        boolean excludeMode = !config.getExcludeExtensions().isEmpty() || !config.getExcludeNamesOrPaths().isEmpty();
        if (excludeMode) {
            // Ignore if matched by exclude rules
            if (matchesNameOrPath(rel, name, config.getExcludeNamesOrPaths())) return true;
            String ext = extensionOf(name);
            if (!ext.isEmpty() && config.getExcludeExtensions().contains(ext)) return true;
            return false;
        }

        // If no excludes configured, do not ignore here; file inclusion handled separately.
        return false;
    }

    private static boolean shouldIncludeFile(Path file, Path rootPath, Config config) {
        String name = file.getFileName().toString();
        String rel = relativizeSafe(rootPath, file);

        // If exclude mode active, include everything not excluded (already filtered in shouldIgnore)
        boolean excludeMode = !config.getExcludeExtensions().isEmpty() || !config.getExcludeNamesOrPaths().isEmpty();
        if (excludeMode) return true;

        // If include lists empty, include all
        boolean hasInclude = !config.getIncludeExtensions().isEmpty() || !config.getIncludeNamesOrPaths().isEmpty();
        if (!hasInclude) return true;

        // Include if matches include rules
        if (matchesNameOrPath(rel, name, config.getIncludeNamesOrPaths())) return true;
        String ext = extensionOf(name);
        return !ext.isEmpty() && config.getIncludeExtensions().contains(ext);
    }

    private static boolean matchesNameOrPath(String relativePath, String name, Set<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return false;
        if (patterns.contains(name)) return true;
        String rel = relativePath; // already '/' normalized by relativizeSafe
        if (patterns.contains(rel)) return true;
        for (String p : patterns) {
            if (p == null || p.isEmpty()) continue;
            String norm = p.replace("\\", "/");
            if (rel.equals(norm)) return true;
            if (rel.startsWith(norm.endsWith("/") ? norm : norm + "/")) return true;
        }
        return false;
    }

    private static String relativizeSafe(Path root, Path path) {
        try {
            return root.relativize(path.toAbsolutePath().normalize()).toString().replace("\\", "/");
        } catch (Exception e) {
            return path.getFileName().toString();
        }
    }

    private static String extensionOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx).toLowerCase() : "";
    }
}
