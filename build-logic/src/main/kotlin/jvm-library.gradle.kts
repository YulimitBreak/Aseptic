plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
