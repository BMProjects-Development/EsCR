import com.google.gson.Gson

plugins {
    base
    idea
    id("me.modmuss50.mod-publish-plugin")
    id("team.0mods.yaml2json") apply false
    kotlin("jvm") apply false
    kotlin("plugin.serialization") apply false
}

base {
    version = "${providers.gradleProperty("mod.version").get()}+mc${providers.gradleProperty("libs.minecraft").get()}"
}

subprojects {
    plugins.apply("idea")

    idea {
        module {
            inheritOutputDirs = false
            outputDir = layout.buildDirectory.dir("idea/classes/main").get().asFile
            testOutputDir = layout.buildDirectory.dir("idea/classes/test").get().asFile
        }
    }
}

val generatedMetaDir = layout.buildDirectory.dir("generated-meta")
val generateModMetadata = tasks.register("generateModMetadata") {
    outputs.dir(generatedMetaDir)

    val bootstrapProjects = subprojects.filter { it.name.startsWith("bootstrap") }

    val jarNameProviders = bootstrapProjects.associate { sub ->
        val provider = sub.tasks.named<Jar>("jar").flatMap { it.archiveFileName }
        inputs.property("${sub.name}JarName", provider)
        sub.name to provider
    }

    doLast {
        val baseDir = generatedMetaDir.get().asFile
        val gson = Gson()

        val fabricJars = mutableListOf<Map<String, String>>()
        val neoForgeJars = mutableListOf<Map<String, Any>>()

        bootstrapProjects.forEach { sub ->
            val jarName = jarNameProviders[sub.name]?.get() ?: return@forEach
            val path = "META-INF/jarjar/$jarName"

            when {
                sub.name.contains("fabric", ignoreCase = true) -> fabricJars.add(mapOf("file" to path))
                sub.name.contains("neoforge", ignoreCase = true) -> neoForgeJars.add(mapOf(
                    "identifier" to mapOf("group" to sub.group.toString(), "artifact" to sub.name),
                    "version" to mapOf("range" to "[${sub.version})", "artifactVersion" to sub.version.toString()),
                    "path" to path,
                    "isObfuscated" to false
                ))
            }
        }

        val fabMeta = File(baseDir, "fabric.mod.json")
        fabMeta.parentFile.mkdirs()
        fabMeta.writeText(gson.toJson(mapOf(
            "schemaVersion" to 1,
            "id" to "${providers.gradleProperty("mod.id").get()}_loader",
            "version" to project.version.toString(),
            "name" to "${base.archivesName.get()} Wrapper",
            "jars" to fabricJars
        )))

        val neoForgeMeta = File(baseDir, "META-INF/jarjar/metadata.json")
        neoForgeMeta.parentFile.mkdirs()
        neoForgeMeta.writeText(
            gson.toJson(
                mapOf("jars" to neoForgeJars)
            )
        )

        val modId = providers.gradleProperty("mod.id").get()
        val modName = base.archivesName.get()
        val modVersion = project.version.toString()
        val modLicense = providers.gradleProperty("mod.license")
            .orElse("All Rights Reserved")
            .get()

        val neoForgeModMetadata = File(
            baseDir,
            "META-INF/neoforge.mods.toml"
        )

        neoForgeModMetadata.parentFile.mkdirs()
        neoForgeModMetadata.writeText(
            """
                modLoader="javafml"
                loaderVersion="[1)"
                license="$modLicense"

                [[mods]]
                modId="${modId}_loader"
                version="$modVersion"
                displayName="$modName Wrapper"
                description='''Universal loader wrapper for $modName.'''
            """.trimIndent()
        )
    }
}

val buildUniversalFatJar = tasks.register<Jar>("buildFatJar") {
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    dependsOn(generateModMetadata)
    from(generateModMetadata)

    val bootstrapProjects = subprojects.filter { it.name.startsWith("bootstrap") }

    bootstrapProjects.forEach { sub ->
        val jarTask = sub.tasks.named<Jar>("jar")
        dependsOn(jarTask)
        from(jarTask.map { it.archiveFile }) {
            into("META-INF/jarjar")
        }
    }
}

tasks.assemble {
    dependsOn(buildUniversalFatJar)
}
