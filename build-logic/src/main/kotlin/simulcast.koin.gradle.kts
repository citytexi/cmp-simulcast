plugins {
    id("simulcast.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.lib("koin-core"))
        }
        commonTest.dependencies {
            implementation(libs.lib("koin-test"))
        }
    }
}
