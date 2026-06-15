plugins {
    alias(libs.plugins.kotlin.jvm)    apply false
    alias(libs.plugins.ksp)           apply false
    alias(libs.plugins.micronaut.app) apply false
    alias(libs.plugins.micronaut.lib) apply false
}

subprojects {
    group   = "dev.temporalflow"
    version = "1.0.0-SNAPSHOT"
}
