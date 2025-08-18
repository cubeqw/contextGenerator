# Project Context Analyzer

This Java tool analyzes a project directory, generating a single Markdown file (`project_structure.md`) that provides an overview of the project's structure and includes the content of specified files. It's useful for quickly sharing a project's context, for documentation, or for feeding into AI tools.

## Features

*   **Project Tree:** Generates a visual tree representation of the project's directory structure.
*   **File Inclusion:** Includes the full content of files within the project in the output Markdown file.
*   **Configurable Filtering:**
    *   Include only files with specific extensions (e.g., `.java`, `.xml`).
    *   Exclude specific files or directories by name (e.g., `.git`, `target`, `context_config.yaml`).
*   **Syntax Highlighting:** Automatically detects common file types and adds language identifiers to code blocks in the Markdown output for better readability on platforms that support it (e.g., GitHub).
*   **Self-Contained Output:** Produces a single Markdown file containing both structure and content, making it easy to share or process.

## Getting Started

### Prerequisites

*   **Java Runtime Environment (JRE):** Java 17 or higher is required to run the pre-compiled JAR.
*   **(Optional for building):** Maven (if you want to compile from source).

### Installation

1.  Download the latest `context.jar` file from the [Releases](https://github.com/cubeqw/contextGenerator/releases) section.
2.  *(Optional)* Place the `context.jar` file in a directory included in your system's `PATH` for easier execution, or note its location.

### Usage

Open a terminal or command prompt.

1.  **Navigate** to the root directory of the project you want to analyze, or prepare to specify the path.
2.  **Run the JAR:**
    ```bash
    # Analyze the current directory
    java -jar /path/to/context.jar

    # Analyze a specific directory (provide the path as an argument)
    java -jar /path/to/context.jar /path/to/your/project/directory
    ```
3.  **(Optional) Generate a default configuration file:**
    ```bash
    java -jar /path/to/context.jar --config
    ```
    This creates a `context_config.yaml` file in the current directory with default settings and comments explaining how to configure the tool. Edit this file to customize which files are included or excluded.
4.  **Re-run the analysis** (after creating/editing `context_config.yaml`) to apply your configuration:
    ```bash
    java -jar /path/to/context.jar
    ```
5.  **Check the output:** After running, a file named `project_structure.md` will be created in the root of the analyzed project directory.

### Configuration (`context_config.yaml`)

The `context_config.yaml` file allows you to control the analysis:

*   `includeExtensions`: A list of file extensions to include in the output content (e.g., `- ".java"`, `- ".xml"`). If this list is empty, *all* file contents are included (subject to `excludeNames`).
*   `excludeNames`: A list of file or directory names (including hidden ones like `.git`) to ignore completely during the analysis (e.g., `- ".git"`, `- "target"`).

Example `context_config.yaml`:
```yaml
# File extensions to include in the analysis (including the dot)
# An empty list means all files are included.
includeExtensions:
  - ".java"
  - ".xml"
  - ".md"

# File or directory names to ignore (including hidden items starting with '.')
excludeNames:
  - ".git"
  - "context_config.yaml"
  - ".idea"
  - "project_structure.md"
  - "README.md"
  - "target"
  - "out"
```
Note: The output file project_structure.md is always added to the internal exclusion list automatically. 
Building from Source (Optional) 

If you prefer to build the JAR yourself: 

Ensure you have Java JDK 17+ and Maven installed.
Clone or download this repository.
Navigate to the project root directory (containing pom.xml).
Run the Maven command:
```bash
    mvn clean package
```

The compiled JAR file will be located in the target/ directory (e.g., target/context-1.0-SNAPSHOT.jar).
