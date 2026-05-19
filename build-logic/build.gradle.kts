plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.android.gradle.plugin)
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
