plugins {
    java
    id("dev.architectury.loom-no-remap")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

base {
    version = "${providers.gradleProperty("mod.version").get()}+mc${providers.gradleProperty("libs.minecraft").get()}"
    archivesName = "${rootProject.base.archivesName.get()}-API"
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

    compileOnly(libs.bundles.kotlinx.serialization)
    compileOnly(libs.bundles.kotlinx.coroutines)
    compileOnly(kotlin("reflect"))

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.bundles.kotlinx.serialization)

    compileOnly("org.spongepowered:mixin:0.8.7")
    compileOnly("org.ow2.asm:asm:9.9.1")
}

tasks.test {
    useJUnit()
}
