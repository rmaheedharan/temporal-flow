plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        moduleName.set(project.name)   // avoids KSP "Can't escape identifier" on Kotlin 2.4.0
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.temporal.sdk)
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.json.schema)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.temporal.testing)
}

tasks.test {
    useJUnitPlatform()
}
