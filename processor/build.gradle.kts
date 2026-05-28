plugins {
    id("jvm-library")
}

group = "io.github.yulimitbreak.aseptic"

dependencies {
    implementation(project(":core"))
    implementation(libs.ksp.api)

    testImplementation(libs.kotest.engine)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kctfork.core)
    testImplementation(libs.kctfork.ksp)
    testImplementation(libs.kotlinxCoroutines.test)
}
