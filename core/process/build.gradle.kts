plugins {
    id("simulcast.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
        }
    }
}
