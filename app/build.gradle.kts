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
            implementation(project(":core:process"))
            implementation(project(":data"))
            implementation(project(":domain"))
            implementation(project(":feature:devices"))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
    }
}
