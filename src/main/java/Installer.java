import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.regex.Pattern;

public class Installer {
    public static boolean performInstall() {
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
            var codeSource = Ctxgen.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path loc = Paths.get(codeSource.getLocation().toURI());
                if (loc.toString().toLowerCase().endsWith(".jar")) {
                    return loc.toAbsolutePath().normalize();
                }
            }
        } catch (Exception ignored) { }

        // Fallback: look for a built jar in common output directories
        Path[] candidates = new Path[] {
                Paths.get("target"),
                Paths.get("out", "artifacts", "ctxgen_jar"),
                Paths.get("out", "artifacts", "context_jar")
        };
        for (Path dir : candidates) {
            try {
                if (Files.isDirectory(dir)) {
                    Path preferred = null, legacy = null, first = null;
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.jar")) {
                        for (Path p : ds) {
                            String fn = p.getFileName().toString();
                            if (fn.startsWith("ctxgen-")) { preferred = p; break; }
                            if (legacy == null && fn.startsWith("context-")) legacy = p;
                            if (first == null) first = p;
                        }
                    }
                    if (preferred != null) return preferred.toAbsolutePath().normalize();
                    if (legacy != null) return legacy.toAbsolutePath().normalize();
                    if (first != null) return first.toAbsolutePath().normalize();
                }
            } catch (IOException ignored) { }
        }
        return null;
    }

    private static boolean installOnWindows(Path jarPath) {
        String wrapper = "@echo off\r\n" +
                "setlocal\r\n" +
                "java -jar \"" + jarPath.toString() + "\" %*\r\n" +
                "endlocal\r\n";

        // Install next to the JAR (preferred behavior on Windows)
        Path installDir = jarPath.getParent();
        if (installDir == null) installDir = Paths.get(".").toAbsolutePath().normalize();

        Path cmd = installDir.resolve("ctxgen.cmd");
        try {
            Files.writeString(cmd, wrapper, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to write wrapper at: " + cmd + ". " + e.getMessage());
            return false;
        }

        System.out.println("Windows install complete: " + cmd);
        // Try to add folder to user PATH if missing
        try {
            String pathEnv = System.getenv("PATH");
            String dirStr = installDir.toString();
            boolean onPath = pathEnv != null && Arrays.stream(pathEnv.split(Pattern.quote(File.pathSeparator)))
                    .map(String::trim)
                    .anyMatch(p -> p.equalsIgnoreCase(dirStr));
            if (!onPath) {
                String newPath = (pathEnv == null || pathEnv.isBlank()) ? dirStr : pathEnv + File.pathSeparator + dirStr;
                new ProcessBuilder("cmd", "/c", "setx", "PATH", newPath)
                        .inheritIO()
                        .start()
                        .waitFor();
                System.out.println("Added to PATH for future sessions: " + dirStr);
                System.out.println("Restart terminal or log out/in for changes to take effect.");
            }
        } catch (Exception e) {
            System.out.println("Could not update PATH automatically. Add to PATH manually: " + installDir);
        }
        return true;
    }

    private static boolean installOnUnix(Path jarPath) {
        String script = "#!/bin/sh\n" +
                "exec java -jar \"" + jarPath.toString() + "\" \"$@\"\n";

        Path target = Paths.get("/usr/local/bin/ctxgen");
        try {
            writeExecutable(target, script);
            System.out.println("Installed to /usr/local/bin. You can run: ctxgen --config");
            return true;
        } catch (Exception e) {
            Path home = Paths.get(System.getProperty("user.home"));
            Path localBin = home.resolve(".local/bin");
            try { Files.createDirectories(localBin); } catch (IOException ignored) {}
            target = localBin.resolve("ctxgen");
            try {
                writeExecutable(target, script);
                System.out.println("Installed to " + target + ". Ensure '~/.local/bin' is in PATH.");
                return true;
            } catch (Exception e2) {
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
        } catch (UnsupportedOperationException ignored) { }
    }
}
