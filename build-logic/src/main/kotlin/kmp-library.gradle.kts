plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(11)
    jvm()

    sourceSets {
        commonMain.dependencies {}
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
