// Top-level build file — declare plugins here as `apply false`, apply them per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// ── Build output lives on an ASCII path ────────────────────────────────────
//
// This project sits at `C:\سطح المكتب\autoinsta` — the parent folder is Arabic.
// Windows reports `sun.jnu.encoding=Cp1252`, which cannot represent those characters,
// so every JVM Gradle forks receives a mangled classpath. Compilation survives (it
// happens in-process) but the forked **test worker** does not: it fails with
// `ClassNotFoundException` for a class Gradle had just compiled and located.
//
// Keeping generated output on an ASCII path sidesteps the whole class of problem —
// tests, lint, and packaging all fork JVMs. Source stays exactly where it is.
//
// Override with `-PbuildRoot=<path>` if this location is inconvenient.
val buildRoot: String = (findProperty("buildRoot") as String?) ?: "C:/autoinsta-build"

rootProject.layout.buildDirectory.set(file("$buildRoot/root"))
subprojects {
    layout.buildDirectory.set(file("$buildRoot/${project.name}"))
}
