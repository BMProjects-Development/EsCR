package com.algorithmlx.escr.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GameLaunchOptions {
    Path directory;
    Path artifact;
    String username;
    String debug;
    boolean dryRun;
    final List<String> jvm = new ArrayList<>();
    final List<String> game = new ArrayList<>();
    final List<Path> mods = new ArrayList<>();

    GameLaunchOptions(String[] args, Path workspace, Path defaultDirectory, String defaultUsername) {
        directory = defaultDirectory;
        username = defaultUsername;
        for (int index = 1; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case "--run-dir" -> directory = workspace.resolve(value(args, ++index, arg)).normalize();
                case "--username" -> username = value(args, ++index, arg);
                case "--jvm-arg" -> jvm.add(value(args, ++index, arg));
                case "--mod" -> mods.add(workspace.resolve(value(args, ++index, arg)).normalize());
                case "--artifact" -> artifact = workspace.resolve(value(args, ++index, arg)).normalize();
                case "--debug" -> debug = "5005";
                case "--dry-run" -> dryRun = true;
                case "--" -> {
                    while (++index < args.length) game.add(args[index]);
                }
                default -> {
                    if (arg.startsWith("--debug=")) debug = arg.substring("--debug=".length());
                    else game.add(arg);
                }
            }
        }
        if (debug != null && (!debug.matches("[0-9]+") || Integer.parseInt(debug) < 1 || Integer.parseInt(debug) > 65535))
            throw new IllegalArgumentException("Debug port must be between 1 and 65535");
    }

    private String value(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
        return args[index];
    }
}
