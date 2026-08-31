plugins {
    id("simulcast.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.material3)
            implementation(project(":core:common"))
        }
    }
}
