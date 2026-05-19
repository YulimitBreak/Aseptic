plugins {
    alias(libs.plugins.detekt)
}

detekt {
    source.setFrom(
        fileTree(rootDir) {
            include("**/src/**/*.kt")
            exclude("**/build/**", "**/sample-android/**")
        }
    )
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}
