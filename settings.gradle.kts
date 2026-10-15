pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "nativegen"

if (gradle.parent == null) {
    includeBuild("../sweety") {
        dependencySubstitution {
            substitute(module("dev.sweety.util:util-math")).using(project(":util:math"))
            substitute(module("dev.sweety.util:util-buffer-panama")).using(project(":util:buffer-panama"))
        }
    }
}


include("annotations", "processor", "nativegen-runtime", "nativegen-spi", "fnv-plugin")
include("native:cpp", "native:rust")
include("examples:hash", "examples:mathops", "examples:buffer")

