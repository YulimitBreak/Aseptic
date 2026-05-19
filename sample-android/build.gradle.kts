plugins {
    id("android-app")
}

android {
    namespace = "io.github.yulimitbreak.aseptic.sample"
}

dependencies {
    implementation(project(":core"))
}
