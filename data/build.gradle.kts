plugins {
    id("simulcast.kmp")
    id("simulcast.serialization")
    id("simulcast.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
            implementation(project(":core:process"))
        }
    }
}
