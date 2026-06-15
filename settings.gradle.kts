rootProject.name = "temporal-flow"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "temporal-flow-core",
    "temporal-flow-micronaut",
    "sample-app"
)
