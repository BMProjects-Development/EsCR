package com.algorithmlx.escr.build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ArchiveTool {
    private static final LocalDateTime ZIP_EPOCH = LocalDateTime.of(1980, 1, 1, 0, 0);
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final byte[] MANIFEST = "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws IOException {
        switch (args[0]) {
            case "module" -> module(Path.of(args[1]), object(Path.of(args[2])));
            case "universal" -> universal(Path.of(args[1]), object(Path.of(args[2])), Path.of(args[3]), Path.of(args[4]));
            default -> throw new IllegalArgumentException("Unknown archive operation: " + args[0]);
        }
    }

    private static JsonObject object(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void module(Path specification, JsonObject project) throws IOException {
        JsonObject spec = object(specification);
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("META-INF/MANIFEST.MF", MANIFEST);
        for (JsonElement input : spec.getAsJsonArray("jars")) {
            try (ZipFile jar = new ZipFile(input.getAsString())) {
                for (ZipEntry entry : jar.stream().filter(entry -> !entry.isDirectory()).toList()) {
                    String name = entry.getName();
                    if (name.equals("META-INF/MANIFEST.MF")) continue;
                    add(entries, name, jar.getInputStream(entry).readAllBytes());
                }
            }
        }
        ResourceProcessor processor = new ResourceProcessor(project);
        for (JsonElement input : spec.getAsJsonArray("resources")) {
            JsonObject resource = input.getAsJsonObject();
            ResourceProcessor.Entry entry = processor.process(Path.of(resource.get("source").getAsString()), resource.get("target").getAsString());
            add(entries, entry.name(), entry.content());
        }
        write(Path.of(spec.get("output").getAsString()), entries);
    }

    private static void add(Map<String, byte[]> entries, String name, byte[] content) {
        byte[] previous = entries.putIfAbsent(name, content);
        if (previous != null && !Arrays.equals(previous, content))
            throw new IllegalArgumentException("Conflicting archive entry: " + name);
    }

    private static void universal(Path output, JsonObject project, Path fabric, Path neoforge) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("META-INF/MANIFEST.MF", MANIFEST);
        String fabricPath = "META-INF/jarjar/" + fabric.getFileName();
        String neoPath = "META-INF/jarjar/" + neoforge.getFileName();
        entries.put(fabricPath, Files.readAllBytes(fabric));
        entries.put(neoPath, Files.readAllBytes(neoforge));
        String version = project.get("version").getAsString();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("schemaVersion", 1);
        metadata.addProperty("id", project.get("mod_id").getAsString() + "_loader");
        metadata.addProperty("version", version);
        metadata.addProperty("name", project.get("archives_name").getAsString() + " Wrapper");
        JsonObject nested = new JsonObject();
        nested.addProperty("file", fabricPath);
        JsonArray jars = new JsonArray();
        jars.add(nested);
        metadata.add("jars", jars);
        entries.put("fabric.mod.json", bytes(JSON.toJson(metadata)));
        String toml = "modLoader=\"javafml\"\nloaderVersion=\"[1,)\"\nlicense=\"" + project.get("mod_license").getAsString()
            + "\"\n[[mods]]\nmodId=\"" + project.get("mod_id").getAsString() + "_loader\"\nversion=\"" + version
            + "\"\ndisplayName=\"" + project.get("archives_name").getAsString() + " Wrapper\"\ndescription='''Universal loader wrapper for "
            + project.get("archives_name").getAsString() + ".'''\n";
        entries.put("META-INF/neoforge.mods.toml", bytes(toml));
        JsonObject identifier = new JsonObject();
        identifier.addProperty("group", project.get("mod_group").getAsString());
        identifier.addProperty("artifact", project.get("mod_id").getAsString() + "-neoforge");
        JsonObject artifactVersion = new JsonObject();
        artifactVersion.addProperty("range", "[" + version + ",)");
        artifactVersion.addProperty("artifactVersion", version);
        JsonObject jar = new JsonObject();
        jar.add("identifier", identifier);
        jar.add("version", artifactVersion);
        jar.addProperty("path", neoPath);
        jar.addProperty("isObfuscated", false);
        JsonArray jarList = new JsonArray();
        jarList.add(jar);
        JsonObject jarJar = new JsonObject();
        jarJar.add("jars", jarList);
        entries.put("META-INF/jarjar/metadata.json", bytes(JSON.toJson(jarJar)));
        write(output, entries);
    }

    private static byte[] bytes(String text) {
        return (text + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void write(Path output, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(output))) {
            for (Map.Entry<String, byte[]> file : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTimeLocal(ZIP_EPOCH);
                archive.putNextEntry(entry);
                archive.write(file.getValue());
                archive.closeEntry();
            }
        }
    }
}
