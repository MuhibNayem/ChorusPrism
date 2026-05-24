rootProject.name = "chorus-observe-server"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://build.shibboleth.net/maven/releases/")
            mavenContent {
                includeGroup("org.opensaml")
                includeGroup("net.shibboleth")
            }
        }
    }
}
