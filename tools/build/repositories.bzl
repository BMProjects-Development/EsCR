_PROJECT_ATTRIBUTES = {
    "archives_name": attr.string(mandatory = True),
    "mod_id": attr.string(mandatory = True),
    "mod_name": attr.string(mandatory = True),
    "mod_version": attr.string(mandatory = True),
    "mod_group": attr.string(mandatory = True),
    "mod_description": attr.string(),
    "mod_license": attr.string(mandatory = True),
    "mod_authors": attr.string(mandatory = True),
    "minecraft_version": attr.string(mandatory = True),
    "kotlin_version": attr.string(mandatory = True),
    "fabric_loader_version": attr.string(mandatory = True),
    "neoforge_version": attr.string(mandatory = True),
    "neoform_version": attr.string(mandatory = True),
    "klf_version": attr.string(mandatory = True),
    "java_version": attr.int(mandatory = True),
    "development_username": attr.string(default = "DevPlayer"),
}

def _project_impl(ctx):
    project = json.decode(ctx.attr.settings)
    project["version"] = project["mod_version"] + "+mc" + project["minecraft_version"]
    ctx.file("project.json", json.encode_indent(project) + "\n")
    ctx.file("config.bzl", "PROJECT = dict(" + repr(project) + ', config_label = Label("//:project.json"))\n')
    ctx.file("BUILD.bazel", 'exports_files(["project.json", "config.bzl"], visibility = ["//visibility:public"])\n')

_project_repository = repository_rule(
    implementation = _project_impl,
    attrs = {"settings": attr.string(mandatory = True)},
)

def _repositories_impl(ctx):
    projects = [tag for module in ctx.modules if module.is_root for tag in module.tags.project]
    if not projects:
        fail("At least one root ecr.project configuration is required")
    names = []
    for project in projects:
        if project.name in names:
            fail("Duplicate project configuration: " + project.name)
        names.append(project.name)
        settings = {name: getattr(project, name) for name in _PROJECT_ATTRIBUTES}
        _project_repository(name = project.name, settings = json.encode(settings))
    return ctx.extension_metadata(root_module_direct_deps = names, root_module_direct_dev_deps = [])

ecr_repositories = module_extension(
    implementation = _repositories_impl,
    tag_classes = {"project": tag_class(attrs = dict(_PROJECT_ATTRIBUTES, name = attr.string(default = "ecr_project")))},
)
