# Essential Craft: Reimagined (escr)

## What is it?

Essential Craft: Reimagined is a mod that created entirely from scratch by me (AlgorithmLX).

The original idea belongs to Essential Craft 3 and the developer Modbder.

The idea for 4-th part of version 1.12.2 belongs to TheLMiffy1111.

## Development setup

Install JDK 25 and set `JAVA_HOME` to its root directory. On Windows, Git Bash must also be available to Bazel.

Run `./bazelw build //:prepare_project` on Linux/macOS or `.\bazelw.cmd build //:prepare_project` on Windows. The target prepares the modules' external compile/runtime dependencies, available dependency sources, Minecraft sources, access wideners, NeoForge's patched development JAR and client assets. It can be rerun after dependency changes and uses Bazel's cache.

Open the repository in IntelliJ IDEA with the JetBrains Bazel plugin, load `.bazelproject`, select JDK 25 and run Bazel Sync. Wait for sync and indexing to finish before checking code analysis. Preparation builds the artifacts used by sync. IDEA still needs to import its own workspace model.

The project inspection profile disables Kotlin's `PackageDirectoryMismatch` inspection because the Bazel importer uses source roots without package-to-directory correspondence. Kotlin package declarations remain authoritative for compilation.

## Authors

Original mod: Modbder, TheLMiffy1111.

Code: AlgorithmLX

Textures: IWaithI and DeadBlack1

## Copyright

All terms and licenses are outlined in [LICENSE.txt](./LICENSE.txt)

---

This project is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft.
