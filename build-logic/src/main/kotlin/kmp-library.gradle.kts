plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

kotlin {
    jvmToolchain(11)
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        commonMain.dependencies {}
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.engine)
            implementation(libs.kotest.assertions)
            implementation(libs.kotest.property)
            implementation(libs.kotlinxCoroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}
