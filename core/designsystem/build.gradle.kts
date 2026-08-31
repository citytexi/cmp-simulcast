plugins {
    id("simulcast.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.material3)
            implementation(project(":core:common"))
        }
    }
}
