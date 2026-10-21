load("@ecr_project//:config.bzl", "PROJECT")
load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

_ARCHIVE_TOOL = Label("//tools/build:archive_tool")

COMMON_DEPS = [
    "@maven//:org_jetbrains_kotlin_kotlin_reflect",
    "@maven//:org_jetbrains_kotlinx_kotlinx_serialization_core_jvm",
    "@maven//:org_jetbrains_kotlinx_kotlinx_serialization_json_jvm",
    "@maven//:org_jetbrains_kotlinx_kotlinx_coroutines_core_jvm",
    "@maven//:org_spongepowered_mixin",
    "@maven//:org_ow2_asm_asm",
]

_MINECRAFT_TARGETS = {
    "": "//tools/build:minecraft",
    "fabric": "//tools/build:fabric_minecraft",
    "neoforge": "//tools/build:neoforge_minecraft",
}

def _module_archive_impl(ctx):
    output = ctx.actions.declare_file(ctx.attr.archive_name)
    jars = [jar for target in ctx.attr.compiled for jar in target[JavaInfo].runtime_output_jars] + ctx.files.merge
    resources = [
        {"source": file.path, "target": file.short_path.split("/src/main/resources/", 1)[1]}
        for file in ctx.files.resources
    ]
    spec = ctx.actions.declare_file(ctx.label.name + ".archive.json")
    ctx.actions.write(spec, json.encode({"jars": [jar.path for jar in jars], "resources": resources, "output": output.path}))
    ctx.actions.run(
        executable = ctx.executable._tool,
        arguments = ["module", spec.path, ctx.file.project.path],
        inputs = depset(jars + ctx.files.resources + [spec, ctx.file.project]),
        outputs = [output],
        mnemonic = "ModArchive",
        progress_message = "Assembling " + ctx.attr.archive_name,
    )
    return [DefaultInfo(files = depset([output]))]

module_archive = rule(
    implementation = _module_archive_impl,
    attrs = {
        "archive_name": attr.string(mandatory = True),
        "compiled": attr.label_list(providers = [JavaInfo]),
        "merge": attr.label_list(allow_files = [".jar"]),
        "resources": attr.label_list(allow_files = True),
        "_tool": attr.label(default = _ARCHIVE_TOOL, executable = True, cfg = "exec"),
        "project": attr.label(default = "@ecr_project//:project.json", allow_single_file = True),
    },
)

def ecr_module(name, suffix, deps = [], merge = [], platform = "", project = PROJECT):
    if platform not in _MINECRAFT_TARGETS:
        fail("Unsupported EsCR loader: " + platform)
    kt_jvm_library(
        name = "classes",
        srcs = native.glob(["src/main/kotlin/**/*.kt", "src/main/java/**/*.java"], allow_empty = True),
        module_name = project["mod_id"] + "_" + name.replace("-", "_"),
        plugins = ["//tools/build:serialization"],
        deps = COMMON_DEPS + deps + [_MINECRAFT_TARGETS[platform]],
        visibility = ["//visibility:public"],
    )
    module_archive(
        name = name,
        archive_name = project["archives_name"] + "-" + suffix + "-" + project["version"] + ".jar",
        compiled = [":classes"],
        merge = merge,
        resources = native.glob(["src/main/resources/**"], allow_empty = True),
        project = project["config_label"],
        visibility = ["//visibility:public"],
    )

def universal_mod(name, fabric, neoforge, project = PROJECT):
    archive_name = project["archives_name"] + "-" + project["version"] + ".jar"
    command = '"$(execpath %s)" universal "$@" "$(location %s)" "$(location %s)" "$(location %s)"' % (
        _ARCHIVE_TOOL, project["config_label"], fabric, neoforge,
    )
    native.genrule(
        name = name,
        srcs = [project["config_label"], fabric, neoforge],
        outs = [archive_name],
        tools = [_ARCHIVE_TOOL],
        cmd = command,
        cmd_bat = '"' + command + '"',
        output_to_bindir = True,
        message = "Assembling " + archive_name,
    )
