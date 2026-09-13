plugins {
    id("dev.architectury.loom-no-remap")
    id("team.0mods.yaml2json")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

base {
    version = "${providers.gradleProperty("mod.version").get()}+mc${providers.gradleProperty("libs.minecraft").get()}"
    archivesName = "${rootProject.base.archivesName.get()}-runtime"
}

val minecraftVersion = rootProject.providers.gradleProperty("libs.minecraft").get()
val modPlatforms = rootProject.providers
    .gradleProperty("mod.platforms")
    .get()
    .split(',')
    .map(String::trim)
    .toTypedArray()

repositories {
    maven("https://repo.spongepowered.org/repository/maven-public/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")

    implementation(project(":api"))

    compileOnly(kotlin("reflect", version = providers.gradleProperty("libs.kotlin").get()))
    compileOnly(libs.bundles.kotlinx.serialization)
    compileOnly(libs.bundles.kotlinx.coroutines)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.bundles.kotlinx.serialization)

    compileOnly("org.spongepowered:mixin:0.8.7")
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.5")
}

tasks.test {
    useJUnit()
}

tasks.yaml2json {
    flatJsonMarker = $$"#$json_flat"
}
