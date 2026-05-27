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

tasks.withType<Test> {
    systemProperty("kotest.framework.config.fqn", "io.github.yulimitbreak.aseptic.GlobalKotestConfig")
}