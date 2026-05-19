plugins {
    id("jvm-library")
}

group = "io.github.yulimitbreak.aseptic"

dependencies {
    implementation(project(":core"))
    implementation(libs.ksp.api)
}
