import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.io.File;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

class BazelBootstrap {
    private static final String RELEASES = "https://github.com/bazelbuild/bazel/releases/download/";

    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() < 25) throw new IllegalStateException("EsCR requires a Java 25 JDK or newer");
        var root = Path.of(System.getProperty("escr.workspace")).toRealPath();
        var version = Files.readString(root.resolve(".bazelversion")).strip();
        var pins = new Properties();
        try (var input = Files.newInputStream(root.resolve("tools/build/bazel-checksums.properties"))) {
            pins.load(input);
        }
        var platform = platform();
        var expected = pins.getProperty(version + "." + platform);
        if (expected == null) throw new IllegalArgumentException("No verified Bazel release for " + version + " / " + platform);
        var directory = cache().resolve("releases").resolve(version);
        Files.createDirectories(directory);
        var executable = directory.resolve(platform.startsWith("windows") ? "bazel.exe" : "bazel");
        if (!Files.isRegularFile(executable) || !digest(executable).equals(expected)) {
            var download = Files.createTempFile(directory, "bazel-", ".download");
            try {
                var uri = URI.create(RELEASES + version + "/bazel-" + version + "-" + platform);
                var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();
                var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).build(), HttpResponse.BodyHandlers.ofFile(download));
                if (response.statusCode() != 200) throw new IOException("Bazel download failed: HTTP " + response.statusCode());
                if (!digest(download).equals(expected)) throw new IOException("Bazel download checksum mismatch");
                Files.move(download, executable, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(download);
            }
        }
        if (!platform.startsWith("windows") && !executable.toFile().setExecutable(true))
            throw new IOException("Cannot make Bazel executable: " + executable);
        var command = new ArrayList<String>();
        command.add(executable.toString());
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).directory(root.toFile()).inheritIO();
        builder.environment().putIfAbsent("JAVA_HOME", System.getProperty("java.home"));
        if (platform.startsWith("windows") && !builder.environment().containsKey("BAZEL_SH")) {
            var bash = findBash();
            if (bash != null) builder.environment().put("BAZEL_SH", bash.toString());
        }
        System.exit(builder.start().waitFor());
    }

    private static Path findBash() {
        for (String entry : System.getenv("PATH").split(File.pathSeparator)) {
            if (entry.isBlank()) continue;
            Path directory = Path.of(entry);
            for (Path candidate : List.of(directory.resolve("bash.exe"), directory.resolve("../bin/bash.exe"), directory.resolve("../usr/bin/bash.exe")))
                if (Files.isRegularFile(candidate)) return candidate.normalize().toAbsolutePath();
        }
        return null;
    }

    private static String platform() {
        var os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        var arch = System.getProperty("os.arch");
        var arm = arch.equals("aarch64") || arch.equals("arm64");
        if (!arm && !arch.equals("amd64") && !arch.equals("x86_64"))
            throw new IllegalArgumentException("Unsupported architecture: " + arch);
        if (os.contains("win") && !arm) return "windows-x86_64.exe";
        if (os.contains("linux")) return "linux-" + (arm ? "arm64" : "x86_64");
        if (os.contains("mac")) return "darwin-" + (arm ? "arm64" : "x86_64");
        throw new IllegalArgumentException("Unsupported operating system: " + os + " / " + arch);
    }

    private static Path cache() {
        var environment = System.getenv();
        var local = environment.get("LOCALAPPDATA");
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") && local != null)
            return Path.of(local, "bazel");
        var xdg = environment.get("XDG_CACHE_HOME");
        return (xdg == null || xdg.isBlank() ? Path.of(System.getProperty("user.home"), ".cache") : Path.of(xdg)).resolve("bazel");
    }

    private static String digest(Path file) throws Exception {
        var hash = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            var buffer = new byte[65536];
            for (int count; (count = input.read(buffer)) >= 0;) hash.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(hash.digest());
    }
}
