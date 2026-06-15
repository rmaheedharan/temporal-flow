plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.lib)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        moduleName.set(project.name)   // avoids KSP "Can't escape identifier" on Kotlin 2.4.0
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

micronaut {
    version(libs.versions.micronaut.bom.get())
    processing {
        incremental(true)
        annotations("dev.temporalflow.micronaut.*")
    }
}

dependencies {
    api(project(":temporal-flow-core"))

    ksp(libs.micronaut.inject.java)

    implementation(libs.temporal.sdk)
    implementation(libs.micronaut.http.server.netty)
    implementation(libs.micronaut.inject.kotlin)
    implementation(libs.micronaut.jackson)
    implementation(libs.jackson.json.schema)
}
