import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Ctxgen {

    private static final String OUTPUT_FILENAME = "project_structure.md";
    private static final String CONFIG_FILENAME = "context_config.yaml";
    private static final String VERSION = "2.0";

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
        // Normalize primary command (support aliases)
        String cmd = args.length > 0 ? normalize(args[0]) : "--help";

        // Help/version
        if ("--version".equalsIgnoreCase(cmd) || "-v".equalsIgnoreCase(cmd)) {
            System.out.println("ctxgen " + resolveVersion());
            return;
        }

        if (args.length == 0 || "--help".equalsIgnoreCase(cmd)) {
            printHelp();
            return;
        }

        if ("--config".equalsIgnoreCase(cmd)) {
            createDefaultConfigV2();
            System.out.println("Configuration file '" + CONFIG_FILENAME + "' created.");
            return;
        }

        if ("--install".equalsIgnoreCase(cmd)) {
            boolean ok = Installer.performInstall();
            if (ok) {
                System.out.println("Installed launcher 'ctxgen'. Try: ctxgen --config");
            } else {
                System.err.println("Installation was not completed. See messages above for next steps.");
            }
            return;
        }

        // List saved profiles
        if ("--list".equalsIgnoreCase(cmd)) {
            var profiles = ConfigStore.listProfiles();
            if (profiles.isEmpty()) {
                System.out.println("No profiles saved. Create one with: ctxgen --save <name>");
            } else {
                System.out.println("Profiles (" + profiles.size() + "):");
                for (String n : profiles) System.out.println("- " + n);
            }
            return;
        }

        // Delete saved profile
        if (args.length > 1 && "--delete".equalsIgnoreCase(cmd)) {
            String name = args[1];
            ConfigStore.deleteProfile(name);
            return;
        }

        // Save named profile from current context_config.yaml
        if (args.length > 1 && "--save".equalsIgnoreCase(cmd)) {
            String name = args[1];
            boolean ok = ConfigStore.saveFrom(Paths.get(CONFIG_FILENAME), name);
            if (!ok) {
                System.err.println("Nothing saved. Ensure '" + CONFIG_FILENAME + "' exists in current directory.");
            }
            return;
        }

        // Use named profile for this run (and write it locally)
        AnalyzerConfig config = null;
        String profileName = null;
        int argIndex = 0;
        if (args.length > 1 && "--use".equalsIgnoreCase(cmd)) {
            profileName = args[1];
            config = ConfigStore.loadNamed(profileName);
            if (config == null) {
                System.err.println("Cannot continue without a valid profile.");
                return;
            }
            // also materialize profile into current directory as context_config.yaml
            try {
                Path src = ConfigStore.pathForName(profileName);
                Files.copy(src, Paths.get(CONFIG_FILENAME), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Wrote profile to ./" + CONFIG_FILENAME + " (from '" + profileName + "').");
            } catch (Exception e) {
                System.out.println("Profile loaded, but failed to copy to ./" + CONFIG_FILENAME + ": " + e.getMessage());
            }
            argIndex = 2; // optional path may follow
        }

        // Explicit generation command using current local config
        boolean doGen = false;
        if (profileName != null) {
            doGen = true; // --use triggers generation
        } else if ("--gen".equalsIgnoreCase(cmd)) {
            doGen = true;
            argIndex = 1; // optional path after --gen
        } else {
            // Unknown or missing command: show help
            printHelp();
            return;
        }

        if (config == null) {
            config = loadConfig();
        }

        String path = ".";
        if (args.length > argIndex) {
            // treat the next arg as the project path
            path = args[argIndex];
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
            if (profileName != null) {
                writer.write("Profile: " + profileName + "\n");
            }
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

    private static void printHelp() {
        System.out.println("ctxgen — project context generator (v" + resolveVersion() + ")");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  ctxgen --help  | -h                   Show this help");
        System.out.println("  ctxgen --version | -v                 Show version");
        System.out.println("  ctxgen --install | -i                 Install 'ctxgen' launcher into PATH");
        System.out.println("  ctxgen --config  | -c                 Create default context_config.yaml in current directory");
        System.out.println("  ctxgen --gen [path] | -g [path]       Generate project_structure.md for current or given path");
        System.out.println("  ctxgen --save <name> | -s <name>      Save ./context_config.yaml as named profile");
        System.out.println("  ctxgen --use <name> [path] | -u       Use profile for generation; also writes ./context_config.yaml");
        System.out.println("  ctxgen --list | -l                    List saved profiles");
        System.out.println("  ctxgen --delete <name> | -d <name>    Delete saved profile");
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  - Exclude lists take precedence over include lists in config.");
        System.out.println("  - Profiles are stored per-user (APPDATA/Library/.config).");
    }

    private static String normalize(String arg0) {
        if (arg0 == null) return "--help";
        switch (arg0) {
            case "-h": return "--help";
            case "-v": return "--version";
            case "-i": return "--install";
            case "-c": return "--config";
            case "-g": return "--gen";
            case "-s": return "--save";
            case "-u": return "--use";
            case "-l": return "--list";
            case "-d": return "--delete";
            default: return arg0;
        }
    }

    private static String resolveVersion() {
        try {
            String impl = Ctxgen.class.getPackage().getImplementationVersion();
            if (impl != null && !impl.isBlank()) return impl;
        } catch (Exception ignored) {}
        return VERSION;
    }

    private static AnalyzerConfig loadConfig() {
        Path configPath = Paths.get(CONFIG_FILENAME);
        if (!Files.exists(configPath)) {
            System.out.println("Configuration file '" + CONFIG_FILENAME + "' not found. Using default settings (all lists are empty).");
            AnalyzerConfig defaultConfig = new AnalyzerConfig();
            defaultConfig.getExcludeNamesOrPaths().add(OUTPUT_FILENAME);
            return defaultConfig;
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(AnalyzerConfig.class, loaderOptions);
        Yaml yaml = new Yaml(constructor);

        try (InputStream inputStream = Files.newInputStream(configPath)) {
            AnalyzerConfig config = yaml.load(inputStream);
            if (config == null) {
                System.out.println("Configuration file '" + CONFIG_FILENAME + "' is empty. Using default settings.");
                config = new AnalyzerConfig();
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
            AnalyzerConfig defaultConfig = new AnalyzerConfig();
            defaultConfig.getExcludeNamesOrPaths().add(OUTPUT_FILENAME);
            return defaultConfig;
        }
    }

    private static void generateTree(BufferedWriter writer, Path currentPath, Path rootPath, int depth, AnalyzerConfig config) throws IOException {
        try {
            var entries = Files.list(currentPath)
                    .filter(p -> !Selection.shouldIgnore(p, rootPath, config))
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

    private static void processFiles(BufferedWriter writer, Path currentPath, Path rootPath, AnalyzerConfig config) throws IOException {
        try {
            var entries = Files.list(currentPath)
                    .filter(p -> !Selection.shouldIgnore(p, rootPath, config))
                    .sorted()
                    .toList();

            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    processFiles(writer, entry, rootPath, config);
                } else {
                    if (Selection.shouldIncludeFile(entry, rootPath, config)) {
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
}
