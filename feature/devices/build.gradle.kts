plugins {
    id("simulcast.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.material3)
        }
    }
}
