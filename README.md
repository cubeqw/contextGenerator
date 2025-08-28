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

Option A — one‑shot installer (recommended):

```bash
java -jar target/ctxgen-2.0.jar --install
```

This creates a `ctxgen` launcher:
- Windows: writes `ctxgen.cmd` next to the JAR (same folder) and attempts to add that folder to your user PATH automatically (new terminals will pick it up).
- Linux/macOS: installs to `/usr/local/bin/ctxgen` or falls back to `~/.local/bin/ctxgen` (ensure it’s on PATH).

Option B — run the JAR directly: download the latest release and use `java -jar /path/to/ctxgen.jar`.

### Usage

Open a terminal or command prompt.

1.  **Navigate** to the root directory of the project you want to analyze, or prepare to specify the path.
2.  **Run the tool (commands):**
    ```bash
    ctxgen --help                 # show help
    ctxgen --install              # install launcher into PATH
    ctxgen --config               # create default context_config.yaml in current folder
    ctxgen --gen [path]           # generate project_structure.md for current or given path
    ctxgen --save <name>          # save ./context_config.yaml as named profile
    ctxgen --use <name> [path]    # use named profile; also writes ./context_config.yaml
    ctxgen --list                 # list saved profiles
    ctxgen --delete <name>        # delete saved profile

    # Or via JAR directly
    java -jar /path/to/ctxgen.jar --gen [path]
    ```

#### Profiles (save and reuse configs)

- Save current `context_config.yaml` as profile:
  - `ctxgen --save php`
  - Stores under user config directory (e.g., `%APPDATA%\ctxgen\php.yaml`, `~/.config/ctxgen/php.yaml`, or `~/Library/Application Support/ctxgen/php.yaml`).
- Use a saved profile for analysis:
  - `ctxgen --use php`
  - Optional path can follow: `ctxgen --use php /path/to/project`
  - The selected profile is also written to `./context_config.yaml` (overwrites if exists).
- List all saved profiles:
  - `ctxgen --list`
- Delete a profile:
  - `ctxgen --delete php`
3.  **(Optional) Generate a default configuration file:**
    ```bash
    java -jar /path/to/ctxgen.jar --config
    ```
    This creates a `context_config.yaml` file in the current directory with default settings and comments explaining how to configure the tool. Edit this file to customize which files are included or excluded.
4.  **Re-run the analysis** (after creating/editing `context_config.yaml`) to apply your configuration:
    ```bash
    java -jar /path/to/ctxgen.jar
    ```
5.  **Check the output:** After running, a file named `project_structure.md` will be created in the root of the analyzed project directory.

### Configuration (`context_config.yaml`)

The `context_config.yaml` file controls which files are included. There are four lists:

* `excludeNamesOrPaths`: names or relative paths to exclude (e.g., `.git`, `target`, `docs/assets`).
* `excludeExtensions`: extensions to exclude (with dot).
* `includeNamesOrPaths`: names or relative paths to include.
* `includeExtensions`: extensions to include (with dot).

Precedence: if any exclude-list is non‑empty, include‑lists are ignored. Otherwise, include‑lists control selection (empty include‑lists mean include all).

Example `context_config.yaml`:
```yaml
includeExtensions: []     # e.g., [".java", ".xml", ".md"]
includeNamesOrPaths: []   # e.g., ["src/main/java", "README.md"]
excludeExtensions: []     # e.g., [".class", ".bin"]
excludeNamesOrPaths:
  - ".git"
  - ".idea"
  - "context_config.yaml"
  - "project_structure.md"
  - "README.md"
  - "target"
  - "out"
```
Note: The output file `project_structure.md` is always excluded automatically.
Building from Source (Optional) 

If you prefer to build the JAR yourself: 

Ensure you have Java JDK 17+ and Maven installed.
Clone or download this repository.
Navigate to the project root directory (containing pom.xml).
Run the Maven command:
```bash
    mvn clean package
```

The compiled JAR file will be located in the target/ directory (e.g., target/ctxgen-2.0.jar).
