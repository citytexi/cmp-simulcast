plugins {
    id("simulcast.desktop.app")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(project(":core:designsystem"))
        }
    }
}
