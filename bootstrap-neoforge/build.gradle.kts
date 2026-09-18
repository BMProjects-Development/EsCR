plugins {
    id("dev.architectury.loom-no-remap")
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
    archivesName = "${rootProject.base.archivesName.get()}-bootstrap-neoforge"
}

repositories {
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://maven.neoforged.net/releases/")
    maven("https://repo.nyon.dev/releases")
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
}

dependencies {
    val kotlinVersion = providers.gradleProperty("libs.kotlin").get()
    val irisVersion = rootProject.providers.gradleProperty("libs.iris.neoforge").get()
    val irisMcVersion = rootProject.providers.gradleProperty("libs.iris.neoforge.mc").get()

    minecraft("com.mojang:minecraft:$minecraftVersion")
    neoForge(libs.neoforge)

    implementation(project(":api"))
    implementation(project(":runtime"))

    compileOnly(libs.bundles.kotlinx.serialization)
    compileOnly(libs.bundles.kotlinx.coroutines)

    implementation("org.spongepowered:mixin:0.8.7")
    compileOnly("maven.modrinth:iris:$irisVersion+$irisMcVersion-neoforge")


    project.property("mod.depend.klf_loader_version").toString()
    val klfVersion = project.property("mod.depend.klf_version").toString()
    val klfLoaderVersion = project.property("mod.depend.klf_loader_version").toString()
    implementation("dev.nyon:KotlinLangForge:$klfVersion-k$kotlinVersion-$klfLoaderVersion+neoforge")
}

val modMetadata = mapOf(
    "modId" to modId,
    "modVersion" to providers.gradleProperty("mod.version").get(),
    "modName" to providers.gradleProperty("mod.name").get(),
    "modDesc" to providers.gradleProperty("mod.description").get(),
    "modAuthors" to "AlgorithmLX",
    "modLicense" to providers.gradleProperty("mod.license").get(),
    "neoforgeVersion" to libs.versions.neoforge.get(),
    "minecraftVersion" to minecraftVersion,
    "klfVersion" to project.property("mod.depend.klf_version").toString()
)

tasks.processResources {
    inputs.properties(modMetadata)
    filesMatching("**/neoforge.mods.toml") {
        expand(modMetadata)
    }
}


tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(project(":api").tasks.jar.map { zipTree(it.archiveFile) })
    from(project(":runtime").tasks.jar.map { zipTree(it.archiveFile) })
}
