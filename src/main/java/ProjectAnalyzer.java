import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ProjectAnalyzer {

    private static final String OUTPUT_FILENAME = "project_structure.md";
    private static final String CONFIG_FILENAME = "context_config.yaml";

    public static class Config {
        private Set<String> includeExtensions = new HashSet<>();
        private Set<String> excludeNames = new HashSet<>();

        public Config() {}

        public Set<String> getIncludeExtensions() {
            return includeExtensions;
        }

        public void setIncludeExtensions(Set<String> includeExtensions) {
            this.includeExtensions = includeExtensions != null ? includeExtensions : new HashSet<>();
        }

        public Set<String> getExcludeNames() {
            return excludeNames;
        }

        public void setExcludeNames(Set<String> excludeNames) {
            this.excludeNames = excludeNames != null ? excludeNames : new HashSet<>();
        }

        @Override
        public String toString() {
            return "Config{" +
                    "includeExtensions=" + includeExtensions +
                    ", excludeNames=" + excludeNames +
                    '}';
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--config".equalsIgnoreCase(args[0])) {
            createDefaultConfig();
            System.out.println("Configuration file '" + CONFIG_FILENAME + "' created.");
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
            writer.write("Exclude Names: " + config.getExcludeNames() + "\n");
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
            if (config.getExcludeNames() == null) config.setExcludeNames(new HashSet<>());
            config.getExcludeNames().add(OUTPUT_FILENAME);
            return config;
        } catch (Exception e) {
            System.err.println("Error reading/parsing configuration file: " + e.getMessage());
            System.err.println("Using default settings.");
            Config defaultConfig = new Config();
            defaultConfig.getExcludeNames().add(OUTPUT_FILENAME);
            return defaultConfig;
        }
    }

    private static void generateTree(BufferedWriter writer, Path currentPath, Path rootPath, int depth, Config config) throws IOException {
        try {
            var entries = Files.list(currentPath)
                    .filter(p -> !shouldIgnore(p, config))
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
                    .filter(p -> !shouldIgnore(p, config))
                    .sorted()
                    .toList();

            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    processFiles(writer, entry, rootPath, config);
                } else {
                    String fileName = entry.getFileName().toString();
                    String extension = "";
                    int lastDotIndex = fileName.lastIndexOf('.');
                    if (lastDotIndex >= 0) {
                        extension = fileName.substring(lastDotIndex).toLowerCase();
                    } else {
                        extension = "";
                    }

                    boolean shouldInclude;
                    if (config.getIncludeExtensions().isEmpty()) {
                        shouldInclude = true;
                    } else {
                        shouldInclude = config.getIncludeExtensions().contains(extension);
                    }

                    if (shouldInclude) {
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

    private static boolean shouldIgnore(Path path, Config config) {
        String name = path.getFileName().toString();

        if (config.getExcludeNames().contains(name)) {
            return true;
        }

        if (name.startsWith(".")) {
            return true;
        }

        return false;
    }
}