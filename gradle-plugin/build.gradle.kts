plugins {
    id("jvm-library")
    `java-gradle-plugin`
}

group = "io.github.yulimitbreak.aseptic"

dependencies {
    implementation(libs.kotlinGradlePlugin)
}

gradlePlugin {
    plugins {
        create("aseptic") {
            id = "io.github.yulimitbreak.aseptic"
            implementationClass = "io.github.yulimitbreak.aseptic.gradle.AsepticPlugin"
        }
    }
}
