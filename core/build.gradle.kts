plugins {
    id("kmp-library")
}

group = "io.github.yulimitbreak.aseptic"

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinxCoroutines)
        }
    }
}
