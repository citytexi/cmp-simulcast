plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.composeCompilerGradlePlugin)
    implementation(libs.kotlin.serializationGradlePlugin)
    implementation(libs.compose.gradlePlugin)
}
