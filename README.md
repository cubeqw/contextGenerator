# Context Generator for LLM

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A simple command-line tool written in Java that analyzes the structure of Java/Kotlin projects (both Maven and Gradle) and generates a comprehensive Markdown report.

This tool is designed to "walk through the files" of a project directory and produce an output file similar to `project-structure.md`, providing a quick overview of the project's layout and key file contents.

## Features

*   **Project Tree Generation:** Creates a visual representation of the project's directory and file structure.
*   **File Content Inclusion:** Automatically includes the content of key project files in the report:
    *   Build files: `pom.xml`, `build.gradle`, `build.gradle.kts`
    *   Source code files: `.java`, `.kt`
    *   Configuration files: `.xml`
    *   Documentation: `README.md`, `LICENSE`
*   **Smart Filtering:** Ignores common build output directories (`target/`, `build/`), version control directories (`.git`), IDE-specific files (`.idea/`), and other temporary or generated files.
*   **Markdown Output:** Generates a single, easy-to-read Markdown file (`project_structure.md`) within the project root, perfect for documentation, sharing, or processing by other tools (like LLMs).

## Getting Started

### Prerequisites

*   Java JDK 11 or higher installed on your system.

### Usage

1.  **Compile the tool:**
    ```bash
    javac ProjectAnalyzer.java
    ```
2.  **Run the analyzer:**
    ```bash
    java ProjectAnalyzer /path/to/your/java-or-kotlin-project
    ```
3.  **View the results:** After execution, a file named `project_structure.md` will be created in the root of the specified project directory. Open it with any Markdown viewer or text editor.

## How it Works

The `ProjectAnalyzer` performs the following steps:

1.  Takes the path to a project directory as a command-line argument.
2.  Recursively scans the directory structure.
3.  Builds a text-based tree representation of the files and folders, excluding common build/IDE/hidden files.
4.  Identifies files with specific extensions (`.java`, `.kt`, `.xml`, `.gradle`, `.kts`, `.md`, etc.).
5.  Reads the content of these identified files.
6.  Writes the project tree and the content of the key files into a single Markdown file (`project_structure.md`) in the project's root directory.

## Example Output

The generated `project_structure.md` will look something like this:
# Project structure overview
```
└── out/
    └── artifacts/
        └── context_jar/
            └── context.jar [4907 chars]
└── src/
    └── main/
        └── java/
            └── ProjectAnalyzer.java [7211 chars]
        └── resources/
            └── META-INF/
                └── MANIFEST.MF [54 chars]
    └── test/
        └── java/
└── README.md [14717 chars]
└── pom.xml [649 chars]
```

**Path: `pom.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.pk2002pc</groupId>
    <artifactId>context</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

</project>
```

**Path: `src/main/java/ProjectAnalyzer.java`**
```java
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
```
