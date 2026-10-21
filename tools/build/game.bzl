load("@rules_java//java:defs.bzl", "JavaInfo", "java_binary")
load("@ecr_project//:config.bzl", "PROJECT")

def _runfile(file, workspace):
    return file.short_path[3:] if file.short_path.startswith("../") else workspace + "/" + file.short_path

def _launch_config_impl(ctx):
    jars = depset(transitive = [dep[JavaInfo].transitive_runtime_jars for dep in ctx.attr.deps]).to_list()
    game = ctx.attr.minecraft[JavaInfo].runtime_output_jars[0]
    files = depset(jars + ctx.files.assets + ctx.files.assets_version + [ctx.file.mod, ctx.file._project, game]).to_list()
    output = ctx.actions.declare_file(ctx.label.name + ".json")
    ctx.actions.write(output, json.encode_indent({
        "platform": ctx.attr.platform,
        "side": ctx.attr.side,
        "classpath": [_runfile(file, ctx.workspace_name) for file in depset(jars + [ctx.file.mod]).to_list() if file.extension == "jar"],
        "mod": _runfile(ctx.file.mod, ctx.workspace_name),
        "game": _runfile(game, ctx.workspace_name),
        "assets_version": _runfile(ctx.file.assets_version, ctx.workspace_name) if ctx.file.assets_version else None,
        "project": _runfile(ctx.file._project, ctx.workspace_name),
    }))
    return [DefaultInfo(files = depset([output]), runfiles = ctx.runfiles(files = files + [output]))]

_launch_config = rule(
    implementation = _launch_config_impl,
    attrs = {
        "deps": attr.label_list(providers = [JavaInfo]),
        "mod": attr.label(mandatory = True, allow_single_file = True),
        "platform": attr.string(mandatory = True, values = ["fabric", "neoforge"]),
        "side": attr.string(mandatory = True, values = ["client", "server"]),
        "minecraft": attr.label(mandatory = True, providers = [JavaInfo]),
        "assets": attr.label(allow_files = True),
        "assets_version": attr.label(allow_single_file = True),
        "_project": attr.label(default = "@ecr_project//:project.json", allow_single_file = True),
    },
)

def game_launches(platform, mod, deps):
    for side in ["client", "server"]:
        _launch_config(
            name = side + "_config",
            platform = platform,
            side = side,
            mod = mod,
            deps = deps,
            minecraft = "@neoforge//:neoforge" if platform == "neoforge" else "//tools/build:minecraft",
            assets = "@minecraft_assets//:assets_" + PROJECT["minecraft_version"] if side == "client" else None,
            assets_version = "@minecraft_assets//:version_" + PROJECT["minecraft_version"] if side == "client" else None,
        )
        java_binary(
            name = side,
            main_class = "com.algorithmlx.escr.build.GameLauncher",
            runtime_deps = ["//tools/build:game_launcher"],
            data = [":" + side + "_config"],
            args = ["$(rlocationpath :" + side + "_config)"],
        )
