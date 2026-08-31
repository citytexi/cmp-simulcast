plugins {
    id("simulcast.desktop.app")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(project(":core:designsystem"))
        }
    }
}
