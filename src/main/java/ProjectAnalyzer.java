import java.io.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ProjectAnalyzer {

    private static final String OUTPUT_FILENAME = "project_structure.md";
    private static final Set<String> EXTENSIONS_TO_INCLUDE = new HashSet<>();
    static {
        EXTENSIONS_TO_INCLUDE.add(".java");
        EXTENSIONS_TO_INCLUDE.add(".kt");
        EXTENSIONS_TO_INCLUDE.add(".xml");
        EXTENSIONS_TO_INCLUDE.add(".gradle");
        EXTENSIONS_TO_INCLUDE.add(".kts");
//        EXTENSIONS_TO_INCLUDE.add(".md");
        EXTENSIONS_TO_INCLUDE.add("");
    }

    private static final Set<String> IGNORED_NAMES = new HashSet<>();
    static {
        IGNORED_NAMES.add("target");
        IGNORED_NAMES.add("build");
        IGNORED_NAMES.add(".git");
        IGNORED_NAMES.add(".gradle");
        IGNORED_NAMES.add(".idea");
        IGNORED_NAMES.add("node_modules");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java ProjectAnalyzer <project_root_path>");
            System.exit(1);
        }

        Path projectRoot = Paths.get(args[0]);

        if (!Files.exists(projectRoot) || !Files.isDirectory(projectRoot)) {
            System.err.println("Error: Provided path '" + projectRoot.toAbsolutePath() + "' is not a valid directory.");
            System.exit(1);
        }

        Path outputFile = projectRoot.resolve(OUTPUT_FILENAME);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            writer.write("# Project structure overview\n");
            writer.write("```\n");

            generateTree(writer, projectRoot, projectRoot, 0);
            writer.write("```\n");

            processFiles(writer, projectRoot, projectRoot);

            System.out.println("Analysis complete. Output written to: " + outputFile.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("An error occurred during analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void generateTree(BufferedWriter writer, Path currentPath, Path rootPath, int depth) throws IOException {
        try {
            var entries = Files.list(currentPath)
                    .filter(p -> !shouldIgnore(p))
                    .sorted((p1, p2) -> {
                        if (Files.isDirectory(p1) && Files.isRegularFile(p2)) return -1;
                        if (Files.isRegularFile(p1) && Files.isDirectory(p2)) return 1;
                        return p1.getFileName().toString().compareTo(p2.getFileName().toString());
                    })
                    .toList();

            for (Path entry : entries) {
                String indent = "    ".repeat(depth);
                String name = entry.getFileName().toString();

                if (entry.equals(rootPath.resolve(OUTPUT_FILENAME))) {
                    continue;
                }

                if (Files.isDirectory(entry)) {
                    writer.write(indent + "└── " + name + "/\n");
                    generateTree(writer, entry, rootPath, depth + 1);
                } else {
                    long size;
                    try {
                        size = Files.size(entry);
                    } catch (IOException e) {
                        size = 0;
                    }
                    writer.write(indent + "└── " + name + " [" + size + " chars]\n");
                }
            }
        } catch (IOException e) {
            String indent = "    ".repeat(depth);
            writer.write(indent + "└── [inaccessible directory]\n");
        }
    }

    private static void processFiles(BufferedWriter writer, Path currentPath, Path rootPath) throws IOException {
        try {
            var entries = Files.list(currentPath)
                    .filter(p -> !shouldIgnore(p))
                    .sorted()
                    .toList();

            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    processFiles(writer, entry, rootPath);
                } else {
                    String fileName = entry.getFileName().toString();
                    String extension = "";
                    int lastDotIndex = fileName.lastIndexOf('.');
                    if (lastDotIndex > 0) {
                        extension = fileName.substring(lastDotIndex).toLowerCase();
                    } else if (lastDotIndex == -1) {
                        extension = "";
                    }

                    if (EXTENSIONS_TO_INCLUDE.contains(extension)) {
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

            writer.write("\n**Path: `" + relativePath + "`**\n");
            writer.write("```" + detectCodeBlockType(file) + "\n");
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.write("\n");
                }
            }
            writer.write("```\n");
        } catch (IOException e) {
            writer.write("\n**Path: `" + rootPath.relativize(file).toString().replace("\\", "/") + "`**\n");
            writer.write("```\n[Error reading file: " + e.getMessage() + "]\n```\n");
        }
    }

    private static String detectCodeBlockType(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".xml")) {
            return "xml";
        } else if (fileName.endsWith(".gradle")) {
            return "gradle";
        } else if (fileName.endsWith(".kts")) {
            return "kotlin";
        } else if (fileName.endsWith(".java")) {
            return "java";
        } else if (fileName.endsWith(".kt")) {
            return "kotlin";
        } else if (fileName.endsWith(".md")) {
            return "markdown";
        }
        if (fileName.equals("license") || fileName.equals("readme")) {
            return "";
        }
        return "";
    }

    private static boolean shouldIgnore(Path path) {
        String name = path.getFileName().toString();
        if (path.endsWith(OUTPUT_FILENAME)) {
            return true;
        }
        if (IGNORED_NAMES.contains(name)) {
            return true;
        }
        if (name.startsWith(".")) {
            return true;
        }
        return false;
    }
}
