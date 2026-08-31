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
    languageVersion.set(JavaLanguageVersion.of(JVM_TOOLCHAIN))
}

extensions.configure<org.jetbrains.compose.ComposeExtension> {
    extensions.configure<org.jetbrains.compose.desktop.DesktopExtension> {
        application {
            mainClass = "dev.citytexi.simulcast.MainKt"
            javaHome = toolchain21.get().metadata.installationPath.asFile.absolutePath
            nativeDistributions {
                targetFormats(TargetFormat.Dmg)
                packageName = "cmp-simulcast"
                // macOS 번들 버전은 major가 1 이상이어야 한다 — "0.1.0"이면 jpackage가 실패한다.
                // 제품 로드맵은 v0.1이지만 이 값은 그것과 무관하게 1.0.0으로 고정한다.
                packageVersion = "1.0.0"
                macOS {
                    bundleID = "dev.citytexi.simulcast"
                }
            }
        }
    }
}
