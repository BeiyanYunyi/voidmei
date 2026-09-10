pluginManagement {
    repositories {
        providers.gradleProperty("voidmei.mavenMirror").orNull?.let { mirror ->
            maven("$mirror/central")
            maven("$mirror/gradle-plugin")
            maven("$mirror/google")
        }
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        providers.gradleProperty("voidmei.mavenMirror").orNull?.let { mirror ->
            maven("$mirror/central")
            maven("$mirror/google")
        }
        mavenCentral()
        google()
    }
}
rootProject.name = "voidmei"
include(":core", ":desktop")
