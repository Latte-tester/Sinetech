rootProject.name = "CloudstreamPlugins"

include(
    "powerDizi",
    "powerSinema"
)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.vidstige")
                includeGroup("com.github.recloudstream")
            }
        }
    }
}
