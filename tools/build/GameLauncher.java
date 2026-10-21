package com.algorithmlx.escr.build;

import com.google.devtools.build.runfiles.Runfiles;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GameLauncher {
    public static void main(String[] args) throws Exception {
        Runfiles runfiles = Runfiles.create();
        JsonObject config = read(resolve(runfiles, args[0]));
        JsonObject project = read(resolve(runfiles, config.get("project").getAsString()));
        String platform = config.get("platform").getAsString();
        String side = config.get("side").getAsString();
        boolean client = side.equals("client");
        Path workspace = Path.of(System.getenv().getOrDefault("BUILD_WORKSPACE_DIRECTORY", System.getProperty("user.dir")));
        GameLaunchOptions options = new GameLaunchOptions(args, workspace, workspace.resolve("run/" + platform + "-" + side), project.get("development_username").getAsString());
        Path assetsVersion = client ? resolve(runfiles, config.get("assets_version").getAsString()).toRealPath() : null;
        Files.createDirectories(options.directory);
        List<Path> classpath = classpath(runfiles, config);
        for (Path extra : options.mods) {
            if (!Files.isRegularFile(extra)) throw new IOException("Missing development mod: " + extra);
            classpath.add(extra);
        }
        Path mod = resolve(runfiles, config.get("mod").getAsString()).toRealPath();
        if (options.artifact != null) {
            classpath.remove(mod);
            mod = options.artifact.toRealPath();
            classpath.add(mod);
        }
        List<String> command = new ArrayList<>(List.of(
            "-Xmx4G", "-Dfile.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow",
            "-XX:ErrorFile=" + options.directory.resolve(".bazel/hs_err_pid%p.log")
        ));
        if (client) command.addAll(List.of("-XX:StackShadowPages=32", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"));
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac") && client) command.add("-XstartOnFirstThread");
        if (platform.equals("fabric")) {
            command.add("-Dfabric.development=true");
            List<Path> mods = new ArrayList<>(options.mods);
            mods.add(mod);
            command.add("-Dfabric.addMods=" + String.join(File.pathSeparator, mods.stream().map(Path::toString).toList()));
            command.add("-Dfabric.gameJarPath=" + resolve(runfiles, config.get("game").getAsString()).toRealPath());
        } else {
            command.addAll(List.of("--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED", "--add-exports", "jdk.naming.dns/com.sun.jndi.dns=java.naming"));
            command.add("-Djava.net.preferIPv6Addresses=system");
        }
        if (options.debug != null) command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:" + options.debug);
        command.addAll(options.jvm);
        command.add("-cp");
        command.add(String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList()));
        command.add(platform.equals("fabric") ? "net.fabricmc.loader.impl.launch.knot.Knot" + (client ? "Client" : "Server") : "net.neoforged.fml.startup." + (client ? "Client" : "Server"));
        if (client) {
            command.addAll(List.of(
                "--username", options.username, "--uuid", UUID.nameUUIDFromBytes(("OfflinePlayer:" + options.username).getBytes(StandardCharsets.UTF_8)).toString(),
                "--accessToken", "0", "--version", project.get("minecraft_version").getAsString(),
                "--gameDir", options.directory.toString(), "--assetsDir", assetsVersion.getParent().getParent().toString(),
                "--assetIndex", Files.readString(assetsVersion).strip(), "--offlineDeveloperMode", "--versionType", "release"
            ));
        } else command.add("nogui");
        command.addAll(options.game);
        Path argumentDirectory = options.directory.resolve(".bazel");
        Files.createDirectories(argumentDirectory);
        Path arguments = argumentDirectory.resolve("java.args");
        Files.write(arguments, command.stream().map(GameLauncher::quote).toList(), StandardCharsets.UTF_8);
        System.out.println("Launching " + platform + " " + side + ": " + options.directory);
        System.out.println("Java arguments: " + arguments);
        if (options.dryRun) return;
        Path java = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        Process process = new ProcessBuilder(java.toString(), "@" + arguments).directory(options.directory.toFile()).inheritIO().start();
        Thread hook = new Thread(process::destroy);
        Runtime.getRuntime().addShutdownHook(hook);
        int status = process.waitFor();
        Runtime.getRuntime().removeShutdownHook(hook);
        System.exit(status);
    }

    private static List<Path> classpath(Runfiles runfiles, JsonObject config) throws IOException {
        LinkedHashSet<Path> jars = new LinkedHashSet<>();
        for (JsonElement element : config.getAsJsonArray("classpath")) {
            String key = element.getAsString();
            Path jar = resolve(runfiles, key);
            jars.add(jar.toRealPath());
        }
        return new ArrayList<>(jars);
    }

    private static Path resolve(Runfiles runfiles, String key) throws IOException {
        String path = runfiles.rlocation(key);
        if (path == null || !Files.isRegularFile(Path.of(path))) throw new IOException("Missing Bazel runfile: " + key);
        return Path.of(path).toAbsolutePath();
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String quote(String argument) {
        return "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

}
