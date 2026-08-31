plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.lib("kotlinx-coroutines-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.lib("kotlinx-coroutines-test"))
        }
    }
}
