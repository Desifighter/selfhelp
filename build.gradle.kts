// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}

val externalBuildBase = File(System.getProperty("user.home"), "gradle-builds/Self-help")

allprojects {
    layout.buildDirectory.set(File(externalBuildBase, if (project == rootProject) "root" else project.name))
}

