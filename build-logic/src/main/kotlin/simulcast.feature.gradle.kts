plugins {
    id("simulcast.compose")
    id("simulcast.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation(project(":core:designsystem"))
            api(libs.lib("orbit-core"))
            implementation(libs.lib("orbit-viewmodel"))
            implementation(libs.lib("orbit-compose"))
            implementation(libs.lib("koin-compose"))
            implementation(libs.lib("koin-composeViewmodel"))
        }
        commonTest.dependencies {
            implementation(libs.lib("orbit-test"))
            implementation(libs.lib("turbine"))
        }
    }
}
