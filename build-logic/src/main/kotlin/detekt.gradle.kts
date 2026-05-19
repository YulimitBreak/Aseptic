import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()
val onCI = System.getenv("CI") != null
val runFromCommandLine = gradle.startParameter.taskNames.singleOrNull() == "detekt"

detekt {
    source.setFrom(
        fileTree(rootDir) {
            include("**/src/**/*.kt")
            exclude("**/build/**", "**/sample-android/**")
        }
    )
    config.setFrom(files("${rootDir}/detekt.yml"))
    buildUponDefaultConfig = true
    ignoreFailures = !onCI && !runFromCommandLine
    autoCorrect = !onCI && runFromCommandLine
}

tasks.withType<Detekt>().configureEach {
    reports {
        sarif.required.set(onCI)
        html.required.set(!onCI)
        md.required.set(false)
        txt.required.set(false)
        xml.required.set(false)
    }
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}
