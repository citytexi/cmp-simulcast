plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    jvmToolchain(JVM_TOOLCHAIN)

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
