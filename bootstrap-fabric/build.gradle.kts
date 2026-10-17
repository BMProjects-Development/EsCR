plugins {
    id("dev.architectury.loom-no-remap")
    id("team.0mods.yaml2json")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val minecraftVersion = rootProject.providers.gradleProperty("libs.minecraft").get()
val modId = providers.gradleProperty("mod.id").get()
val modPlatforms = rootProject.providers
    .gradleProperty("mod.platforms")
    .get()
    .split(',')
    .map(String::trim)
    .toTypedArray()

base {
    version = "${providers.gradleProperty("mod.version").get()}+mc${providers.gradleProperty("libs.minecraft").get()}"
    archivesName = "${rootProject.base.archivesName.get()}-bootstrap-fabric"
}

repositories {
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://maven.fabricmc.net/")
    exclusiveContent {
        forRepository {
            maven("https://api.modrinth.com/maven") {
                name = "Modrinth"
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

loom {
    mods {
        maybeCreate(modId).apply {
            sourceSet("main")
            sourceSet("main", ":api")
            sourceSet("main", ":runtime")
        }
    }

    runs {
        named("client") {
            programArguments.addAll(listOf("--username", "AlgorithmLX"))
        }
    }
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "escr-vfx-tests"
        enableGameTests = false
        enableClientGameTests = true
        clearRunDirectory = false
    }
}

dependencies {
    val irisVersion = rootProject.providers.gradleProperty("libs.iris").get()

    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation(libs.bundles.fabricmc)
    implementation(libs.fabric.language.kotlin)

    implementation(project(":api"))
    implementation(project(":runtime"))

    compileOnly(libs.bundles.kotlinx.serialization)
    compileOnly(libs.bundles.kotlinx.coroutines)

    compileOnly("org.spongepowered:mixin:0.8.7")
    compileOnly("maven.modrinth:iris:$irisVersion+$minecraftVersion-fabric")
}

val modMetadata = mapOf(
    "modId" to modId,
    "modVersion" to providers.gradleProperty("mod.version").get(),
    "modName" to providers.gradleProperty("mod.name").get(),
    "modDesc" to providers.gradleProperty("mod.description").get(),
    "modAuthors" to "AlgorithmLX",
    "modLicense" to providers.gradleProperty("mod.license").get(),
    "fabricLoaderVersion" to libs.versions.fabric.loader.get(),
    "minecraftVersion" to minecraftVersion
)

tasks.processResources {
    inputs.properties(modMetadata)
    filesMatching("fabric.mod.json") {
        expand(modMetadata)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(project(":api").tasks.jar.map { zipTree(it.archiveFile) })
    from(project(":runtime").tasks.jar.map { zipTree(it.archiveFile) })
}
