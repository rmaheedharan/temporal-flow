plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.app)
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
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("dev.temporalflow.sample.*")
    }
}

application {
    mainClass = "dev.temporalflow.sample.ApplicationKt"
}

dependencies {
    implementation(project(":temporal-flow-micronaut"))

    ksp(libs.micronaut.inject.java)

    implementation(libs.micronaut.inject.kotlin)
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<AbstractTestTask>().configureEach {
    failOnNoDiscoveredTests = false
}
