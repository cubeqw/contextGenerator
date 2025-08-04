# Project Analyzer

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
