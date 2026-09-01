plugins {
    id("simulcast.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.material3)
            implementation(project(":core:common"))
        }
    }
}
