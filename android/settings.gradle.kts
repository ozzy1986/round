pluginManagement {
    repositories {
        maven { url = uri(file("local-maven").toURI()) }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(file("local-maven").toURI()) }
        google()
        mavenCentral()
    }
}
rootProject.name = "Raund"
include(":app")
