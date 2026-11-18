load("@rules_java//java:defs.bzl", "JavaInfo")

PreparationInfo = provider(fields = ["files"])

def _java_files(info):
    return [info.transitive_compile_time_jars, info.transitive_runtime_jars, info.transitive_source_jars]

def _prepare_dependencies_impl(target, ctx):
    files = []
    for name in ["deps", "exports", "runtime_deps"]:
        for dep in getattr(ctx.rule.attr, name, []):
            if PreparationInfo in dep:
                files.append(dep[PreparationInfo].files)
    if ctx.label.workspace_root and JavaInfo in target:
        files.extend(_java_files(target[JavaInfo]))
    return [PreparationInfo(files = depset(transitive = files))]

_prepare_dependencies = aspect(
    implementation = _prepare_dependencies_impl,
    attr_aspects = ["deps", "exports", "runtime_deps"],
)

def _prepare_project_impl(ctx):
    files = [target[PreparationInfo].files for target in ctx.attr.modules]
    for target in ctx.attr.libraries:
        files.extend(_java_files(target[JavaInfo]))
    return [DefaultInfo(files = depset(ctx.files.data, transitive = files))]

_prepare_files = rule(
    implementation = _prepare_project_impl,
    attrs = {
        "modules": attr.label_list(aspects = [_prepare_dependencies], providers = [JavaInfo]),
        "libraries": attr.label_list(providers = [JavaInfo]),
        "data": attr.label_list(allow_files = True),
    },
)

def prepare_project(name, modules, libraries, data = [], visibility = None):
    inputs = name + "_inputs"
    _prepare_files(
        name = inputs,
        modules = modules,
        libraries = libraries,
        data = data,
        visibility = ["//visibility:private"],
        tags = ["manual", "no-ide"],
    )
    native.genrule(
        name = name,
        srcs = [":" + inputs],
        outs = [name + ".ready"],
        cmd = 'echo "Project dependencies, sources and assets prepared." > "$@"',
        cmd_bat = 'echo Project dependencies, sources and assets prepared. > "$@"',
        message = "Preparing development project",
        visibility = visibility,
    )
