plugins {
    id("simulcast.kmp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.lib("kotlinx-serialization-json"))
        }
    }
}
