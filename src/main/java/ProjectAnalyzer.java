import java.io.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ProjectAnalyzer {

    private static final String OUTPUT_FILENAME = "project_structure.md";
    // Множество расширений файлов, содержимое которых нужно выводить
    // Можно расширить по необходимости
    private static final Set<String> EXTENSIONS_TO_INCLUDE = new HashSet<>();
    static {
        EXTENSIONS_TO_INCLUDE.add(".java");
        EXTENSIONS_TO_INCLUDE.add(".kt");
        EXTENSIONS_TO_INCLUDE.add(".xml");
        EXTENSIONS_TO_INCLUDE.add(".gradle");
        EXTENSIONS_TO_INCLUDE.add(".kts");
        EXTENSIONS_TO_INCLUDE.add(".md"); // README.md
        EXTENSIONS_TO_INCLUDE.add(""); // Файлы без расширения, например, LICENSE
        // Добавьте другие расширения при необходимости
    }

    // Множество имен файлов/директорий, которые нужно игнорировать
    private static final Set<String> IGNORED_NAMES = new HashSet<>();
    static {
        IGNORED_NAMES.add("target"); // Maven output
        IGNORED_NAMES.add("build");  // Gradle output
        IGNORED_NAMES.add(".git");
        IGNORED_NAMES.add(".gradle"); // Gradle cache
        IGNORED_NAMES.add(".idea");   // IDE files
        IGNORED_NAMES.add("node_modules"); // JS dependencies
        // Добавьте другие имена при необходимости
    }

    public static void main(String[] args) {
        // Проверяем аргумент командной строки
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

            // 1. Генерируем дерево проекта
            generateTree(writer, projectRoot, projectRoot, 0);
            writer.write("```\n");

            // 2. Проходим по всем файлам и добавляем содержимое нужных
            processFiles(writer, projectRoot, projectRoot);

            System.out.println("Analysis complete. Output written to: " + outputFile.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("An error occurred during analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Рекурсивно генерирует дерево файлов и записывает его в writer.
     */
    private static void generateTree(BufferedWriter writer, Path currentPath, Path rootPath, int depth) throws IOException {
        try {
            // Сортируем: сначала директории, потом файлы
            var entries = Files.list(currentPath)
                    .filter(p -> !shouldIgnore(p)) // Игнорируем определенные файлы/директории
                    .sorted((p1, p2) -> {
                        if (Files.isDirectory(p1) && Files.isRegularFile(p2)) return -1;
                        if (Files.isRegularFile(p1) && Files.isDirectory(p2)) return 1;
                        return p1.getFileName().toString().compareTo(p2.getFileName().toString());
                    })
                    .toList();

            for (Path entry : entries) {
                String indent = "    ".repeat(depth);
                String name = entry.getFileName().toString();

                // Пропускаем выходной файл, чтобы не включать его в само описание
                // (проверка перенесена в shouldIgnore, но оставим на всякий случай)
                if (entry.equals(rootPath.resolve(OUTPUT_FILENAME))) {
                    continue;
                }

                if (Files.isDirectory(entry)) {
                    writer.write(indent + "└── " + name + "/\n");
                    // Рекурсивно обрабатываем поддиректории
                    generateTree(writer, entry, rootPath, depth + 1);
                } else {
                    // Для файлов добавляем размер (примерно как в оригинале)
                    long size;
                    try {
                        size = Files.size(entry);
                    } catch (IOException e) {
                        size = 0; // Если не удалось получить размер
                    }
                    writer.write(indent + "└── " + name + " [" + size + " chars]\n");
                }
            }
        } catch (IOException e) {
            // Если нет доступа к директории, просто пропускаем её
            String indent = "    ".repeat(depth);
            writer.write(indent + "└── [inaccessible directory]\n");
        }
    }

    /**
     * Рекурсивно проходит по всем файлам и добавляет содержимое тех, которые соответствуют критериям.
     */
    private static void processFiles(BufferedWriter writer, Path currentPath, Path rootPath) throws IOException {
        try {
            var entries = Files.list(currentPath)
                    .filter(p -> !shouldIgnore(p)) // Игнорируем определенные файлы/директории
                    .sorted() // Сортировка для предсказуемого порядка
                    .toList();

            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    // Рекурсивно обрабатываем поддиректории
                    processFiles(writer, entry, rootPath);
                } else {
                    String fileName = entry.getFileName().toString();
                    String extension = "";
                    int lastDotIndex = fileName.lastIndexOf('.');
                    if (lastDotIndex > 0) {
                        extension = fileName.substring(lastDotIndex).toLowerCase();
                    } else if (lastDotIndex == -1) {
                        extension = ""; // Файл без расширения
                    }

                    // Проверяем, нужно ли включать содержимое этого файла
                    if (EXTENSIONS_TO_INCLUDE.contains(extension)) {
                        appendFileContent(writer, entry, rootPath);
                    }
                }
            }
        } catch (IOException e) {
            // Просто пропускаем недоступные директории
            System.err.println("Warning: Could not access directory " + currentPath + ": " + e.getMessage());
        }
    }

    /**
     * Добавляет содержимое файла в формате Markdown.
     */
    private static void appendFileContent(BufferedWriter writer, Path file, Path rootPath) throws IOException {
        try {
            // Получаем относительный путь для заголовка
            String relativePath = rootPath.relativize(file).toString().replace("\\", "/"); // Для Windows

            writer.write("\n**Path: `" + relativePath + "`**\n");
            writer.write("```" + detectCodeBlockType(file) + "\n"); // Добавляем подсветку синтаксиса
            // Используем try-with-resources для Reader'а
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line); // Записываем строку как есть
                    writer.write("\n"); // Добавляем перевод строки
                }
            }
            writer.write("```\n");
        } catch (IOException e) {
            writer.write("\n**Path: `" + rootPath.relativize(file).toString().replace("\\", "/") + "`**\n");
            writer.write("```\n[Error reading file: " + e.getMessage() + "]\n```\n");
        }
    }

    /**
     * Простая функция для определения типа блока кода для подсветки синтаксиса.
     */
    private static String detectCodeBlockType(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".xml")) {
            return "xml";
        } else if (fileName.endsWith(".gradle")) {
            return "gradle"; // Или "groovy"
        } else if (fileName.endsWith(".kts")) {
            return "kotlin";
        } else if (fileName.endsWith(".java")) {
            return "java";
        } else if (fileName.endsWith(".kt")) {
            return "kotlin";
        } else if (fileName.endsWith(".md")) {
            return "markdown";
        }
        // Для файлов без расширения (LICENSE, etc.) или неизвестных расширений
        // можно попробовать угадать по имени файла или оставить пустым
        if (fileName.equals("license") || fileName.equals("readme")) {
            return ""; // Обычно без подсветки
        }
        return ""; // Без подсветки по умолчанию
    }

    /**
     * Проверяет, следует ли игнорировать файл или директорию.
     */
    private static boolean shouldIgnore(Path path) {
        String name = path.getFileName().toString();
        // Игнорируем выходной файл
        if (path.endsWith(OUTPUT_FILENAME)) {
            return true;
        }
        // Игнорируем по имени файла/директории
        if (IGNORED_NAMES.contains(name)) {
            return true;
        }
        // Игнорируем скрытые файлы/директории (начинаются с точки)
        if (name.startsWith(".")) {
            return true;
        }
        return false;
    }
}