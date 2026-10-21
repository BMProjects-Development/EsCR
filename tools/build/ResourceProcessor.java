package com.algorithmlx.escr.build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ResourceProcessor {
    private static final String FLAT_MARKER = "#$json_flat";
    private static final String LOWERCASE_MARKER = "#$json_lowercase";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, String> replacements;
    private final Yaml yaml;

    ResourceProcessor(JsonObject project) {
        replacements = new LinkedHashMap<>();
        Map<String, String> names = Map.ofEntries(
            Map.entry("modId", "mod_id"), Map.entry("modVersion", "mod_version"),
            Map.entry("modName", "mod_name"), Map.entry("modDesc", "mod_description"),
            Map.entry("modAuthors", "mod_authors"), Map.entry("modLicense", "mod_license"),
            Map.entry("fabricLoaderVersion", "fabric_loader_version"), Map.entry("minecraftVersion", "minecraft_version"),
            Map.entry("neoforgeVersion", "neoforge_version"), Map.entry("klfVersion", "klf_version")
        );
        names.forEach((key, value) -> replacements.put("${" + key + "}", project.get(value).getAsString()));
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(16777216);
        yaml = new Yaml(new SafeConstructor(options));
    }

    Entry process(Path source, String target) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        if (target.endsWith(".yml") || target.endsWith(".yaml")) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            Object data = yaml.load(text);
            if (text.lines().anyMatch(line -> line.trim().equals(LOWERCASE_MARKER)))
                data = lowercaseKeys(data);
            if (text.lines().anyMatch(line -> line.trim().equals(FLAT_MARKER))) {
                Map<String, Object> flat = new LinkedHashMap<>();
                flatten("", data, flat);
                data = flat;
            }
            target = target.substring(0, target.lastIndexOf('.')) + ".json";
            bytes = (JSON.toJson(data) + "\n").getBytes(StandardCharsets.UTF_8);
        } else if (target.equals("fabric.mod.json") || target.equals("META-INF/neoforge.mods.toml")) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            for (Map.Entry<String, String> replacement : replacements.entrySet())
                text = text.replace(replacement.getKey(), replacement.getValue());
            if (text.contains("${")) throw new IllegalArgumentException("Unexpanded metadata: " + target);
            bytes = text.getBytes(StandardCharsets.UTF_8);
        }
        return new Entry(target, bytes);
    }

    private Object lowercaseKeys(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                String lowercase = String.valueOf(key).toLowerCase(Locale.ROOT);
                if (result.containsKey(lowercase))
                    throw new IllegalArgumentException("Duplicate lowercase JSON key: " + lowercase);
                result.put(lowercase, lowercaseKeys(child));
            });
            return result;
        } else if (value instanceof List<?> list) return list.stream().map(this::lowercaseKeys).toList();
        return value;
    }

    private void flatten(String prefix, Object value, Map<String, Object> output) {
        if (value instanceof Map<?, ?> map)
            map.forEach((key, child) -> flatten(
                key.toString().equals(".") ? prefix : prefix.isEmpty() ? key.toString() : prefix + "." + key, child, output
            ));
        else {
            if (output.containsKey(prefix))
                throw new IllegalArgumentException("Duplicate flattened language key: " + prefix);
            output.put(prefix, value);
        }
    }

    record Entry(String name, byte[] content) {}
}
