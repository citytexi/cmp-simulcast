import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("simulcast.compose")
}

// compose.desktop.application.javaHome defaults to the Gradle daemon's own JVM, not the
// project's kotlin { jvmToolchain(21) } — without pinning it here, :app:run and packageDmg
// launch/bundle against whatever JDK started the daemon, which can be older than 21.
val toolchain21 = the<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

extensions.configure<org.jetbrains.compose.ComposeExtension> {
    extensions.configure<org.jetbrains.compose.desktop.DesktopExtension> {
        application {
            mainClass = "dev.citytexi.simulcast.MainKt"
            javaHome = toolchain21.get().metadata.installationPath.asFile.absolutePath
            nativeDistributions {
                targetFormats(TargetFormat.Dmg)
                packageName = "cmp-simulcast"
                packageVersion = "1.0.0"
                macOS {
                    bundleID = "dev.citytexi.simulcast"
                }
            }
        }
    }
}
