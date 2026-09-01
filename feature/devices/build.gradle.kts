plugins {
    id("simulcast.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.material3)
        }
    }
}
