# cmp-simulcast 초기 스캐폴드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compose Multiplatform macOS 데스크톱 앱의 모듈 골격·빌드 로직·외부 프로세스 실행 계약을 세우고, 연결·정지된 Android/iOS 디바이스 목록을 보여주는 수직 슬라이스 하나로 그 골격을 검증한다.

**Architecture:** KMP `jvm()` 타깃 하나. `app` / `core:{common,designsystem,process}` / `domain` / `data` / `feature:devices` 7개 모듈이 단방향으로 의존하고, `build-logic` composite build의 컨벤션 플러그인이 그 의존 규칙을 물리적으로 강제한다. 외부 도구(adb·emulator·xcrun) 실행은 `core:process`의 `CommandRunner` 하나로 수렴하며 모든 실패를 예외가 아닌 값으로 표현한다.

**Tech Stack:** Kotlin 2.3.21, Compose Multiplatform 1.11.1, Gradle 9.7.1, JVM toolchain 21, Koin 4.2.2, Orbit MVI 12.0.0, kotlinx-serialization 1.11.0, kotlinx-coroutines 1.11.0, kotlin.test + Turbine 1.2.1

**Spec:** [`docs/superpowers/specs/2026-08-21-cmp-simulcast-scaffold-design.md`](../specs/2026-08-21-cmp-simulcast-scaffold-design.md)

## Global Constraints

- Kotlin **2.3.21**. Kotlin 2.4 계열로 올리지 않는다 — Compose Multiplatform 1.11.1은 Kotlin 2.4.0보다 이틀 먼저 나왔고 CHANGELOG에 "2.4"가 없다.
- Compose Multiplatform **1.11.1**. 1.12.0은 rc 단계라 쓰지 않는다.
- Gradle **9.7.1**, JVM toolchain **21**.
- 의존은 단방향이다. **`feature`는 `data`를 참조하지 않는다.** `domain`의 인터페이스만 본다.
- **`core:process`는 앱 지식이 0이다.** adb·simctl·emulator를 모른다. 의존은 `core:common` 하나뿐이다.
- **`core:common`은 아무것도 참조하지 않는다.**
- `CommandRunner`는 **예외를 던지지 않는다.** 기동 실패·타임아웃·비정상 종료를 전부 값으로 반환한다.
- `CancellationException`은 잡지 않고 재던진다.
- `kotlin.Result`를 쓰지 않는다. 실패 타입은 `core:common`의 `Outcome<T, E>`.
- 셸을 거치지 않는다. `ProcessBuilder(executable, *args)`로 직접 실행한다.
- 코드 주석 규약: 코드가 이미 말하는 것은 쓰지 않는다. `@return`·`@param`은 타입·이름이 말하지 못할 때만 단다. 다른 컴포넌트의 현재 상태를 단정하지 않는다(낡는다).
- 커밋은 태스크마다. `main`·`develop`에 직접 커밋하지 않는다. push·PR은 사용자 확인 후.

## 로컬 환경 전제

작업 기계에서 확인된 사실이며 Task 2가 여기에 의존한다.

- `gradle`이 PATH에 **없다.** wrapper를 부트스트랩해야 한다.
- 설치된 JDK 최대 버전이 **17**이다. toolchain 21은 foojay resolver가 자동 프로비저닝한다. Gradle 자신은 JDK 17로 돈다.
- `adb` = `~/Library/Android/sdk/platform-tools/adb`, `emulator` = `~/Library/Android/sdk/emulator/emulator`, `xcrun` = `/usr/bin/xcrun`.

---

# 페이즈 A — 빌드 기반

### Task 1: ADR 스텁 4건

결정 근거를 코드보다 먼저 남긴다. 스펙이 확정한 네 결정을 `proposed`로 세워 두고, 구현이 끝나는 Task 21에서 `accepted`로 올린다.

**Files:**
- Create: `docs/adr/0001-kmp-single-jvm-target.md`
- Create: `docs/adr/0002-koin-di.md`
- Create: `docs/adr/0003-orbit-mvi.md`
- Create: `docs/adr/0004-core-process-failures-as-values.md`
- Modify: `docs/adr/README.md` (인덱스 테이블)

**Interfaces:**
- Consumes: 없음
- Produces: 없음 (문서)

- [ ] **Step 1: ADR-0001 작성**

`docs/adr/0001-kmp-single-jvm-target.md`:

```markdown
---
id: ADR-0001
title: KMP multiplatform 플러그인 + jvm() 타깃 하나
status: proposed
date: 2026-08-21
deciders: cmp-simulcast 팀
supersedes:
superseded_by:
related_adr:
related_architecture:
platforms: desktop
tags: [adr, build]
---

# ADR-0001: KMP multiplatform 플러그인 + jvm() 타깃 하나

## 맥락

이 앱은 macOS 데스크톱 전용이다. 타깃이 하나뿐이면 `kotlin("jvm")` 플러그인으로 충분하고,
KMP는 `commonMain`/`jvmMain` 소스셋 계층이라는 비용만 남는다.

## 결정

`kotlin("multiplatform")` 플러그인을 쓰되 타깃은 `jvm()` 하나만 선언한다.

- 모든 모듈이 `simulcast.kmp` 컨벤션 플러그인 하나를 적용한다.
- 플랫폼 무관 코드는 `commonMain`, JVM API를 쓰는 코드는 `jvmMain`에 둔다.
- `core:process`의 공개 타입은 `commonMain`에 둔다. `java.nio.file.Path` 대신 `String`을
  쓰는 이유가 이것이다 — 공개 API가 `jvmMain`에 갇히면 그것을 참조하는 `data`까지 따라 갇힌다.

## 대안

- **`kotlin("jvm")` 단일 플러그인** — 빌드가 단순하고 소스셋이 하나다. 그러나 나중에
  Windows·Linux 데스크톱을 지원하려면 모듈마다 플러그인과 소스셋 구조를 바꿔야 한다.
  **→ 기각:** 되돌리는 비용이 소스셋 계층을 지금 지는 비용보다 크다.
- **KMP + android 타깃까지** — 코어 로직을 Android 앱에 재사용할 수 있다. 그러나 AGP가
  들어와 build-logic이 두 배가 되고, 이 앱은 데스크톱 도구라 Android에서 돌 일이 없다.
  **→ 기각:** 쓰지 않을 능력에 빌드 복잡도를 지불한다.

## 영향

**긍정**

- 타깃 추가가 `jvm()` 옆에 한 줄 추가로 끝난다.
- 컨벤션 플러그인이 하나라 모듈 build 파일이 `plugins { }` 한 블록이다.

**트레이드오프**

- 지금은 쓰지 않는 `commonMain`/`jvmMain` 분리를 매 모듈이 진다.

**위험·방어**

- 공개 API에 JVM 타입이 새어 들어가면 위 이점이 사라진다. `core:process`의 공개 타입을
  `commonMain`에 두는 것으로 그 규칙을 물리적으로 강제한다.
```

- [ ] **Step 2: ADR-0002 작성**

`docs/adr/0002-koin-di.md`:

```markdown
---
id: ADR-0002
title: DI로 Koin 채택
status: proposed
date: 2026-08-21
deciders: cmp-simulcast 팀
supersedes:
superseded_by:
related_adr: ADR-0001
related_architecture:
platforms: desktop
tags: [adr, di]
---

# ADR-0002: DI로 Koin 채택

## 맥락

Hilt는 Android 전용이라 이 프로젝트에서 쓸 수 없다. feature 모듈이 늘어나는 구조를
전제하므로, 모듈이 추가될 때 조립 지점의 변경이 작아야 한다.

## 결정

Koin을 쓴다. 모듈마다 자기 `module { }` 정의를 소유하고 `app`이 그것들을 모아 시작한다.

- `feature`는 `domain`의 인터페이스만 요구한다. 구현 바인딩은 `app`의 조립에서 일어난다.
- 그래프 검증은 `app` 모듈에서 합성 그래프 하나에 대해 `verify()`로 한다.

## 대안

- **kotlin-inject** — 컴파일 타임에 누락을 잡는다. 그러나 KSP가 모든 모듈에 들어와 빌드가
  느려지고 컨벤션 플러그인에 KSP 배선을 직접 써야 한다.
  **→ 기각:** 이 규모에서 얻는 안전성보다 빌드·설정 비용이 크다.
- **수동 조립(composition root)** — 라이브러리가 0개이고 테스트가 쉽다. 그러나 feature가
  늘 때마다 `app`의 조립 파일이 계속 커진다.
  **→ 기각:** 모듈 추가 비용을 한곳에 몰아 두는 구조를 피한다.

## 영향

**긍정**

- feature 추가 시 `app`의 변경이 모듈 목록 한 줄이다.

**트레이드오프**

- 누락을 런타임에 알게 된다. `verify()`가 이를 테스트 시점으로 당기지만 **생성자 주입만**
  본다 — `single { Foo(get(), "literal") }`처럼 람다에서 조립하는 정의는 사각지대다.

**위험·방어**

- `app` 단위 `verify()` 테스트를 둔다. 람다 조립 정의는 리뷰에서 잡는다.
```

- [ ] **Step 3: ADR-0003 작성**

`docs/adr/0003-orbit-mvi.md`:

```markdown
---
id: ADR-0003
title: 상태관리로 Orbit MVI 채택
status: proposed
date: 2026-08-21
deciders: cmp-simulcast 팀
supersedes:
superseded_by:
related_adr: ADR-0002
related_architecture:
platforms: desktop
tags: [adr, state]
---

# ADR-0003: 상태관리로 Orbit MVI 채택

## 맥락

화면이 늘어날 때 상태 홀더의 모양이 제각각이 되는 것을 막고 싶고, 상태 전이를 테스트로
고정할 수 있어야 한다.

## 결정

Orbit MVI를 쓴다. 상태 홀더는 `ContainerHost<S, E>`를 구현하고 `container { }`로 초기 상태를
선언하며, 상태 전이는 `intent { reduce { } }`로만 일어난다. 테스트는 `orbit-test`로 한다.

`orbit-viewmodel-desktop`·`orbit-compose-desktop` 아티팩트가 있어 데스크톱 타깃을 정식 지원한다.

## 대안

- **androidx ViewModel + StateFlow 직접** — 내장 네이밍이라 새 사람이 바로 읽는다. 그러나
  상태 전이를 강제하는 계약이 없어 화면마다 모양이 갈린다.
  **→ 기각:** 팀이 MVI 계약을 원했다.
- **자체 BaseViewModel<S, I, E>** — 의존이 없다. 그러나 테스트 도구를 직접 만들어야 한다.
  **→ 기각:** `orbit-test`가 이미 그 자리를 채운다.

## 영향

**긍정**

- 모든 화면이 같은 모양이고 상태 전이가 테스트로 고정된다.

**트레이드오프**

- Orbit이 androidx `ViewModel` 기반이라 `lifecycle-viewmodel-compose`와
  `koin-compose-viewmodel`이 함께 따라온다.

**위험·방어**

- 초당 수천 줄이 들어오는 로그 화면에서 `intent` 경유 비용이 문제가 될 수 있다. 로그
  스트리밍 스펙에서 배치 emit과 함께 재검토한다.
```

- [ ] **Step 4: ADR-0004 작성**

`docs/adr/0004-core-process-failures-as-values.md`:

```markdown
---
id: ADR-0004
title: core:process를 data에서 분리하고 모든 실패를 값으로 표현
status: proposed
date: 2026-08-21
deciders: cmp-simulcast 팀
supersedes:
superseded_by:
related_adr: ADR-0001
related_architecture:
platforms: desktop
tags: [adr, process]
---

# ADR-0004: core:process를 data에서 분리하고 모든 실패를 값으로 표현

## 맥락

adb·emulator·xcrun 호출이 전부 "프로세스를 띄우고 줄 단위로 읽는다"로 수렴한다. 이 앱에서
가장 많이 재사용되면서 동시에 테스트하기 가장 어려운 부분이다 — 프로세스 수명, 타임아웃,
손자 프로세스 회수, 파이프 드레인이 모두 여기 모인다.

## 결정

별도 모듈 `core:process`로 분리하고, `CommandRunner`가 **예외를 던지지 않는다.**

- `run`은 `Completed` / `TimedOut` / `StartFailed`를 반환한다.
- `stream`은 `Flow<CommandEvent>`를 돌려주며 `Stdout` / `Stderr` / `Dropped` / `Exited` /
  `StartFailed`를 실어 보낸다.

타임아웃을 값으로 표현하는 것이 특히 중요하다. `withTimeout`이 던지는
`TimeoutCancellationException`은 `CancellationException`의 하위 타입이라, 이것을 잡아 에러로
바꾸면 "`CancellationException`은 재던진다"는 규칙과 충돌한다. 잡지 않으면 타임아웃 에러가
영영 생성되지 않는 죽은 코드가 된다. 값으로 표현하면 그 충돌 자체가 사라진다.

## 대안

- **`data`에 두고 어댑터마다 프로세스를 직접 다룬다** — 모듈이 하나 줄어든다. 그러나 회수·
  드레인 로직이 어댑터마다 복제되고, `data`의 테스트가 통째로 무거워진다.
  **→ 기각:** 가장 어려운 부분을 격리하지 못한다.
- **예외를 던지는 계약** — Kotlin 관용구에 가깝다. 그러나 위의 `CancellationException` 충돌이
  생기고, `Flow<String>`은 기동 실패를 raw `IOException`으로 흘려 경계가 샌다.
  **→ 기각:** 계약이 표현하지 못하는 상태가 남는다.

## 영향

**긍정**

- 프로세스 실행이 앱과 무관하게 혼자 서고 통합 테스트가 이 모듈에 격리된다.
- 호출부가 실패를 `when`으로 다루므로 누락이 컴파일 단계에서 드러난다.

**트레이드오프**

- 반환 타입이 sealed 계층이라 호출부가 항상 분기해야 한다.

**위험·방어**

- `Process.destroy()`는 직계 자식만 죽인다. `adb shell`·`simctl spawn`은 손자를 만들므로
  `ProcessHandle.descendants()`를 역순으로 회수한 뒤 루트를 죽인다. 이를 통합 테스트로 고정한다.
```

- [ ] **Step 5: README 인덱스 등록**

`docs/adr/README.md`의 인덱스 테이블을 교체:

```markdown
| ADR | Title | Status | Date | Postscript |
|-----|-------|--------|------|-----------|
| [0001](0001-kmp-single-jvm-target.md) | KMP multiplatform 플러그인 + jvm() 타깃 하나 | proposed | 2026-08-21 | `kotlin("jvm")` 기각 |
| [0002](0002-koin-di.md) | DI로 Koin 채택 | proposed | 2026-08-21 | `verify()`는 테스트 시점·생성자 주입 한정 |
| [0003](0003-orbit-mvi.md) | 상태관리로 Orbit MVI 채택 | proposed | 2026-08-21 | 로그 화면 성능은 후속 재검토 |
| [0004](0004-core-process-failures-as-values.md) | core:process를 data에서 분리하고 모든 실패를 값으로 표현 | proposed | 2026-08-21 | 타임아웃을 값으로 둬야 취소 규칙과 충돌이 없다 |
```

- [ ] **Step 6: 커밋**

```bash
git add docs/adr
git commit -m "docs: add proposed ADRs for the scaffold decisions"
```

---

### Task 2: Gradle 부트스트랩 · 버전 카탈로그 · build-logic · core:common

**Files:**
- Create: `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`
- Create: `settings.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/build.gradle.kts`
- Create: `build-logic/src/main/kotlin/Catalog.kt`
- Create: `build-logic/src/main/kotlin/simulcast.kmp.gradle.kts`
- Create: `core/common/build.gradle.kts`
- Test: `core/common/src/commonTest/kotlin/dev/citytexi/simulcast/common/ToolchainTest.kt`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: 없음
- Produces: 플러그인 id `simulcast.kmp`. 카탈로그 `libs`. 모듈 좌표 `:core:common`.

- [ ] **Step 1: Gradle 배포판을 임시로 받아 wrapper 생성**

`gradle`이 PATH에 없으므로 배포판을 한 번만 내려받아 wrapper를 만든다. 생성 후 임시 디렉토리는 지운다.

```bash
TMP=$(mktemp -d)
curl -sL https://services.gradle.org/distributions/gradle-9.7.1-bin.zip -o "$TMP/g.zip"
unzip -q "$TMP/g.zip" -d "$TMP"
"$TMP/gradle-9.7.1/bin/gradle" wrapper --gradle-version 9.7.1 --distribution-type bin
rm -rf "$TMP"
ls -la gradlew gradle/wrapper/
```

Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` 생성.

- [ ] **Step 2: 버전 카탈로그 작성**

`gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.3.21"
compose = "1.11.1"
coroutines = "1.11.0"
serializationJson = "1.11.0"
koin = "4.2.2"
orbit = "12.0.0"
turbine = "1.2.1"

[libraries]
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
kotlin-composeCompilerGradlePlugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
kotlin-serializationGradlePlugin = { module = "org.jetbrains.kotlin:kotlin-serialization", version.ref = "kotlin" }
compose-gradlePlugin = { module = "org.jetbrains.compose:compose-gradle-plugin", version.ref = "compose" }

kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-swing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serializationJson" }

koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-composeViewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }
koin-test = { module = "io.insert-koin:koin-test", version.ref = "koin" }

orbit-core = { module = "org.orbit-mvi:orbit-core", version.ref = "orbit" }
orbit-viewmodel = { module = "org.orbit-mvi:orbit-viewmodel", version.ref = "orbit" }
orbit-compose = { module = "org.orbit-mvi:orbit-compose", version.ref = "orbit" }
orbit-test = { module = "org.orbit-mvi:orbit-test", version.ref = "orbit" }

turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
```

- [ ] **Step 3: 루트 settings 작성**

`settings.gradle.kts`. `foojay-resolver-convention`이 toolchain 21을 자동으로 내려받는다 — 이 기계에 설치된 JDK는 17이 최대다.

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cmp-simulcast"

include(":core:common")
```

- [ ] **Step 4: build-logic 골격 작성**

`build-logic/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
```

`build-logic/build.gradle.kts`:

```kotlin
plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.composeCompilerGradlePlugin)
    implementation(libs.kotlin.serializationGradlePlugin)
    implementation(libs.compose.gradlePlugin)
}
```

- [ ] **Step 5: 카탈로그 접근 헬퍼 작성**

precompiled script plugin에서는 타입세이프 `libs` 접근자가 생성되지 않는다. 이 헬퍼 없이는 컨벤션 플러그인 첫 작성에서 막힌다.

`build-logic/src/main/kotlin/Catalog.kt`:

```kotlin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("libs.versions.toml 에 '$alias' 없음") }
```

- [ ] **Step 6: `simulcast.kmp` 컨벤션 플러그인 작성**

`build-logic/src/main/kotlin/simulcast.kmp.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.lib("kotlinx-coroutines-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.lib("kotlinx-coroutines-test"))
        }
    }
}
```

- [ ] **Step 7: 실패하는 테스트 작성**

toolchain 21이 실제로 적용됐는지 확인한다. 이 기계의 기본 JDK는 17이라, 컨벤션 플러그인이 toolchain을 제대로 걸지 않으면 이 테스트가 떨어진다.

`core/common/src/commonTest/kotlin/dev/citytexi/simulcast/common/ToolchainTest.kt`:

```kotlin
package dev.citytexi.simulcast.common

import kotlin.test.Test
import kotlin.test.assertTrue

class ToolchainTest {
    @Test
    fun runs_on_jvm_21() {
        val version = System.getProperty("java.version")
        assertTrue(version.startsWith("21"), "expected JVM 21, got $version")
    }
}
```

- [ ] **Step 8: 테스트 실패 확인**

```bash
./gradlew :core:common:jvmTest
```

Expected: FAIL — `Project 'core' not found` 또는 `core/common/build.gradle.kts` 부재로 설정 단계에서 실패.

- [ ] **Step 9: `core:common` 모듈 추가**

`core/common/build.gradle.kts`:

```kotlin
plugins {
    id("simulcast.kmp")
}
```

- [ ] **Step 10: 테스트 통과 확인**

```bash
./gradlew :core:common:jvmTest
```

Expected: PASS. 첫 실행에서 foojay resolver가 JDK 21을 내려받으므로 시간이 걸린다.

- [ ] **Step 11: `.gitignore` 갱신**

기존 내용 아래에 추가:

```gitignore
# Gradle
.gradle/
build/
local.properties
```

- [ ] **Step 12: 커밋**

```bash
git add gradlew gradlew.bat gradle settings.gradle.kts build-logic core .gitignore
git commit -m "build: add gradle wrapper, version catalog, and the kmp convention plugin"
```

---

### Task 3: Compose 컨벤션 · app 모듈 · 버전 조합 스파이크

빈 모듈 컴파일은 Compose 컴파일러를 거치지 않아 아무것도 증명하지 못한다. 실제로 `@Composable`을 그린 창을 띄우고 dmg까지 만들어 설치 실행한다.

**Files:**
- Create: `build-logic/src/main/kotlin/simulcast.compose.gradle.kts`
- Create: `build-logic/src/main/kotlin/simulcast.desktop.app.gradle.kts`
- Create: `core/designsystem/build.gradle.kts`
- Create: `core/designsystem/src/commonMain/kotlin/dev/citytexi/simulcast/designsystem/AppTheme.kt`
- Create: `app/build.gradle.kts`
- Create: `app/src/jvmMain/kotlin/dev/citytexi/simulcast/Main.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `simulcast.kmp` (Task 2)
- Produces: 플러그인 id `simulcast.compose`, `simulcast.desktop.app`. `AppTheme(content: @Composable () -> Unit)`.

- [ ] **Step 1: `simulcast.compose` 컨벤션 플러그인 작성**

`build-logic/src/main/kotlin/simulcast.compose.gradle.kts`:

```kotlin
plugins {
    id("simulcast.kmp")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
```

- [ ] **Step 2: `simulcast.desktop.app` 컨벤션 플러그인 작성**

`build-logic/src/main/kotlin/simulcast.desktop.app.gradle.kts`:

```kotlin
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
```

- [ ] **Step 3: 테마 작성**

`core/designsystem/src/commonMain/kotlin/dev/citytexi/simulcast/designsystem/AppTheme.kt`:

```kotlin
package dev.citytexi.simulcast.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
```

`core/designsystem/build.gradle.kts`:

```kotlin
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
```

- [ ] **Step 4: app 진입점 작성**

`app/src/jvmMain/kotlin/dev/citytexi/simulcast/Main.kt`:

```kotlin
package dev.citytexi.simulcast

import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.citytexi.simulcast.designsystem.AppTheme

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "cmp-simulcast") {
        AppTheme {
            Text("scaffold")
        }
    }
}
```

`app/build.gradle.kts`:

```kotlin
plugins {
    id("simulcast.desktop.app")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(project(":core:designsystem"))
        }
    }
}
```

- [ ] **Step 5: settings에 모듈 등록**

`settings.gradle.kts`의 `include` 블록을 교체:

```kotlin
include(":app")
include(":core:common")
include(":core:designsystem")
```

- [ ] **Step 6: 창이 실제로 뜨는지 확인 (스파이크 1/2)**

```bash
./gradlew :app:run
```

Expected: "cmp-simulcast" 제목의 창이 뜨고 "scaffold" 텍스트가 보인다. `NoSuchMethodError`·`AbstractMethodError`가 나면 Kotlin 2.3.21 + Compose 1.11.1 조합이 실패한 것이다 — 그 경우 작업을 멈추고 사용자에게 보고한다.

- [ ] **Step 7: dmg 패키징과 설치 실행 확인 (스파이크 2/2)**

```bash
./gradlew :app:packageDmg
ls app/build/compose/binaries/main/dmg/
```

Expected: `cmp-simulcast-1.0.0.dmg` 생성. dmg를 열어 `.app`을 실행하고 창이 뜨는지 확인한다.

`./gradlew run`에서만 확인하면 GUI 환경 변수 문제를 놓친다 — Task 13이 그 차이에 의존한다.

- [ ] **Step 8: 커밋**

```bash
git add build-logic core/designsystem app settings.gradle.kts
git commit -m "build: add compose conventions and the desktop app module"
```

---

# 페이즈 B — core:process

이 페이즈의 테스트는 실존 명령(`/bin/echo`, `/bin/sh`, `/bin/sleep`)을 실제로 실행한다. 검증 대상이 타임아웃 회수·손자 회수·파이프 데드락이라 fake로는 아무것도 증명되지 않는다. 테스트가 `/bin/sh -c`를 쓰는 것은 stderr 대량 출력이나 손자 프로세스를 만들기 위해서다 — **앱 코드는 셸을 거치지 않는다**는 제약은 그대로다.

### Task 4: core:process 모듈 · 타입 · `run` 정상 경로

**Files:**
- Create: `core/process/build.gradle.kts`
- Create: `core/process/src/commonMain/kotlin/dev/citytexi/simulcast/process/Command.kt`
- Create: `core/process/src/commonMain/kotlin/dev/citytexi/simulcast/process/CommandRunner.kt`
- Create: `core/process/src/jvmMain/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunner.kt`
- Test: `core/process/src/jvmTest/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunnerRunTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `simulcast.kmp` (Task 2)
- Produces: `Command(executable: String, args: List<String>, env: Map<String, String>, workingDir: String?)`, `CommandResult.{Completed, TimedOut, StartFailed}`, `CommandEvent.{Stdout, Stderr, Dropped, Exited, StartFailed}`, `CommandRunner.run(Command, Duration): CommandResult`, `CommandRunner.stream(Command, Int): Flow<CommandEvent>`, `ProcessCommandRunner(io: CoroutineDispatcher)`

- [ ] **Step 1: 실패하는 테스트 작성**

`core/process/src/jvmTest/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunnerRunTest.kt`:

```kotlin
package dev.citytexi.simulcast.process

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class ProcessCommandRunnerRunTest {

    private val runner = ProcessCommandRunner()

    @Test
    fun captures_stdout_and_exit_code() = runTest {
        val result = runner.run(Command("/bin/echo", listOf("hello")), 5.seconds)

        val completed = assertIs<CommandResult.Completed>(result)
        assertEquals(0, completed.exitCode)
        assertEquals("hello", completed.stdout.trim())
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :core:process:jvmTest
```

Expected: FAIL — `Project ':core:process' not found`.

- [ ] **Step 3: 타입 작성**

`core/process/src/commonMain/kotlin/dev/citytexi/simulcast/process/Command.kt`:

```kotlin
package dev.citytexi.simulcast.process

/**
 * @param executable 실행 파일의 절대 경로. 셸을 거치지 않으므로 PATH 조회는 호출부 몫이다.
 */
data class Command(
    val executable: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val workingDir: String? = null,
)
```

`core/process/src/commonMain/kotlin/dev/citytexi/simulcast/process/CommandRunner.kt`:

```kotlin
package dev.citytexi.simulcast.process

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

sealed interface CommandResult {
    data class Completed(val exitCode: Int, val stdout: String, val stderr: String) : CommandResult
    data class TimedOut(val partialStdout: String, val partialStderr: String) : CommandResult
    data class StartFailed(val reason: String) : CommandResult
}

sealed interface CommandEvent {
    data class Stdout(val line: String) : CommandEvent
    data class Stderr(val line: String) : CommandEvent
    data class Dropped(val count: Int) : CommandEvent
    data class Exited(val exitCode: Int) : CommandEvent
    data class StartFailed(val reason: String) : CommandEvent
}

interface CommandRunner {
    /** 실패를 던지지 않는다. 기동 실패와 타임아웃도 [CommandResult]로 돌아온다. */
    suspend fun run(command: Command, timeout: Duration): CommandResult

    /** 수집이 취소되면 프로세스와 그 자손을 회수한다. */
    fun stream(command: Command, capacity: Int = DEFAULT_STREAM_CAPACITY): Flow<CommandEvent>

    companion object {
        const val DEFAULT_STREAM_CAPACITY: Int = 4096
    }
}
```

- [ ] **Step 4: 최소 구현 작성**

`core/process/src/jvmMain/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunner.kt`:

```kotlin
package dev.citytexi.simulcast.process

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration

class ProcessCommandRunner(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CommandRunner {

    override suspend fun run(command: Command, timeout: Duration): CommandResult = withContext(io) {
        val process = command.toProcessBuilder().start()
        coroutineScope {
            val stdout = async { process.inputStream.bufferedReader().readText() }
            val stderr = async { process.errorStream.bufferedReader().readText() }
            val exitCode = process.waitFor()
            CommandResult.Completed(exitCode, stdout.await(), stderr.await())
        }
    }

    override fun stream(command: Command, capacity: Int): Flow<CommandEvent> = flow { }
}

internal fun Command.toProcessBuilder(): ProcessBuilder =
    ProcessBuilder(listOf(executable) + args).apply {
        workingDir?.let { directory(File(it)) }
        environment().putAll(env)
    }
```

- [ ] **Step 5: 모듈 등록**

`core/process/build.gradle.kts`:

```kotlin
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
```

`settings.gradle.kts`의 `include` 목록에 추가:

```kotlin
include(":core:process")
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew :core:process:jvmTest
```

Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add core/process settings.gradle.kts
git commit -m "feat(process): add CommandRunner contract and the happy path of run"
```

---

### Task 5: `run` 기동 실패

실행 파일이 없으면 `ProcessBuilder.start()`가 `IOException`을 던진다. exit code가 존재하지 않아 정상 종료로도 실패 종료로도 표현할 수 없고, "Xcode 없는 기계에서 `xcrun`을 못 찾는" 경우가 이 앱의 핵심 시나리오다.

**Files:**
- Modify: `core/process/src/jvmMain/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunner.kt`
- Test: `core/process/src/jvmTest/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunnerRunTest.kt`

**Interfaces:**
- Consumes: Task 4의 `CommandResult`, `ProcessCommandRunner`
- Produces: 없음 (동작 추가)

- [ ] **Step 1: 실패하는 테스트 추가**

`ProcessCommandRunnerRunTest`에 추가:

```kotlin
    @Test
    fun reports_start_failure_without_throwing() = runTest {
        val result = runner.run(Command("/nonexistent/tool"), 5.seconds)

        assertIs<CommandResult.StartFailed>(result)
    }
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :core:process:jvmTest --tests '*ProcessCommandRunnerRunTest*'
```

Expected: FAIL — `java.io.IOException: Cannot run program "/nonexistent/tool"`

- [ ] **Step 3: 구현 수정**

`run`의 프로세스 기동 부분을 교체:

```kotlin
    override suspend fun run(command: Command, timeout: Duration): CommandResult = withContext(io) {
        val process = try {
            command.toProcessBuilder().start()
        } catch (e: IOException) {
            return@withContext CommandResult.StartFailed(e.message ?: e.toString())
        }
        coroutineScope {
            val stdout = async { process.inputStream.bufferedReader().readText() }
            val stderr = async { process.errorStream.bufferedReader().readText() }
            val exitCode = process.waitFor()
            CommandResult.Completed(exitCode, stdout.await(), stderr.await())
        }
    }
```

`import java.io.IOException`를 추가한다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :core:process:jvmTest --tests '*ProcessCommandRunnerRunTest*'
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add core/process
git commit -m "feat(process): return StartFailed instead of throwing when the executable is missing"
```

---

### Task 6: `run` 타임아웃과 프로세스 회수

`withTimeout`을 쓰지 않는다. 그것이 던지는 `TimeoutCancellationException`은 `CancellationException`의 하위 타입이라 "취소는 재던진다"는 규칙과 충돌하고, 협조적 취소라 `readText()`에 블록된 스레드를 깨우지도 못한다 — 코루틴만 빠져나오고 프로세스는 남는다. `Process.waitFor(timeout)`으로 직접 재고, 넘기면 스스로 회수한 뒤 값으로 알린다.

**Files:**
- Modify: `core/process/src/jvmMain/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunner.kt`
- Create: `core/process/src/jvmMain/kotlin/dev/citytexi/simulcast/process/Reap.kt`
- Test: `core/process/src/jvmTest/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunnerRunTest.kt`

**Interfaces:**
- Consumes: Task 5의 `ProcessCommandRunner`
- Produces: `internal fun Process.reapTree(graceMillis: Long = 500)`

- [ ] **Step 1: 실패하는 테스트 추가**

`ProcessCommandRunnerRunTest`에 추가. `runTest`의 가상 시계는 실제 프로세스 대기에 관여하지 않으므로 벽시계로 잰다.

```kotlin
    @Test
    fun times_out_and_kills_the_process() = runTest {
        val started = System.currentTimeMillis()
        val result = runner.run(Command("/bin/sleep", listOf("30")), 300.milliseconds)
        val elapsed = System.currentTimeMillis() - started

        assertIs<CommandResult.TimedOut>(result)
        assertTrue(elapsed < 10_000, "타임아웃이 걸리지 않고 매달렸다: ${elapsed}ms")
    }
```

`import kotlin.time.Duration.Companion.milliseconds`, `import kotlin.test.assertTrue`를 추가한다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :core:process:jvmTest --tests '*ProcessCommandRunnerRunTest*'
```

Expected: FAIL — 30초 뒤 `Completed`가 돌아온다(타임아웃 미구현).

- [ ] **Step 3: 회수 헬퍼 작성**

`core/process/src/jvmMain/kotlin/dev/citytexi/simulcast/process/Reap.kt`:

```kotlin
package dev.citytexi.simulcast.process

import java.util.concurrent.TimeUnit

/**
 * `destroy()` 는 직계 자식에게만 신호를 보낸다. `sh -c 'x & wait'` 류가 만드는 손자는
 * 그대로 남으므로 자손 목록을 먼저 붙잡아 역순으로 죽인다 — 루트를 먼저 죽이면 자손이
 * 재부모화되어 목록에서 사라진다.
 */
internal fun Process.reapTree(graceMillis: Long = 500) {
    val descendants = descendants().toList()
    descendants.asReversed().forEach { it.destroy() }
    destroy()
    if (!waitFor(graceMillis, TimeUnit.MILLISECONDS)) {
        descendants.asReversed().forEach { it.destroyForcibly() }
        destroyForcibly()
        waitFor()
    }
    descendants.forEach { it.destroyForcibly() }
}
```

- [ ] **Step 4: `run` 수정**

```kotlin
    override suspend fun run(command: Command, timeout: Duration): CommandResult = withContext(io) {
        val process = try {
            command.toProcessBuilder().start()
        } catch (e: IOException) {
            return@withContext CommandResult.StartFailed(e.message ?: e.toString())
        }
        coroutineScope {
            val stdout = async { process.inputStream.bufferedReader().readText() }
            val stderr = async { process.errorStream.bufferedReader().readText() }

            val exited = process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            if (!exited) {
                process.reapTree()
                return@coroutineScope CommandResult.TimedOut(stdout.await(), stderr.await())
            }
            CommandResult.Completed(process.exitValue(), stdout.await(), stderr.await())
        }
    }
```

`import java.util.concurrent.TimeUnit`를 추가한다. 회수 뒤에 `await()`를 하는 순서가 중요하다 — 프로세스가 죽어야 파이프에 EOF가 오고 `readText()`가 끝난다.

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :core:process:jvmTest --tests '*ProcessCommandRunnerRunTest*'
```

Expected: PASS (3개 테스트 전부)

- [ ] **Step 6: 커밋**

```bash
git add core/process
git commit -m "feat(process): time out via waitFor and reap the process tree"
```

---

### Task 7: stderr 대량 출력에서 데드락 없음

stdout과 stderr는 별개 파이프다. 한쪽만 읽으면 다른 쪽 OS 버퍼가 차는 순간 자식이 write에서 영구 블록되고 부모는 EOF를 영영 못 본다. `adb`는 daemon 기동 메시지를, `xcrun`은 실패 메시지를 stderr로 뱉으므로 이 앱이 다루는 두 도구가 정확히 이 함정을 밟는다. Task 4에서 이미 양쪽을 `async`로 읽고 있으므로 이 태스크는 그 성질을 회귀 테스트로 고정한다.

**Files:**
- Test: `core/process/src/jvmTest/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunnerPipeTest.kt`

**Interfaces:**
- Consumes: Task 6의 `ProcessCommandRunner`
- Produces: 없음 (테스트)

- [ ] **Step 1: 테스트 작성**

`core/process/src/jvmTest/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunnerPipeTest.kt`:

```kotlin
package dev.citytexi.simulcast.process

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ProcessCommandRunnerPipeTest {

    private val runner = ProcessCommandRunner()

    @Test
    fun drains_stderr_larger_than_the_os_pipe_buffer() = runTest {
        val script = "i=0; while [ \$i -lt 20000 ]; do echo 'stderr line' 1>&2; i=\$((i+1)); done; echo done"
        val result = runner.run(Command("/bin/sh", listOf("-c", script)), 30.seconds)

        val completed = assertIs<CommandResult.Completed>(result)
        assertEquals(0, completed.exitCode)
        assertEquals("done", completed.stdout.trim())
        assertTrue(completed.stderr.length > 100_000, "stderr 가 잘렸다: ${completed.stderr.length}")
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :core:process:jvmTest --tests '*ProcessCommandRunnerPipeTest*'
```

Expected: PASS. 실패하거나 30초 타임아웃으로 `TimedOut`이 나온다면 stdout·stderr를 동시에 드레인하지 않는 구현으로 회귀한 것이다.

- [ ] **Step 3: 커밋**

```bash
git add core/process
git commit -m "test(process): pin concurrent stdout and stderr draining"
```

---

### Task 8: `stream` 기본 동작

**Files:**
- Modify: `core/process/src/jvmMain/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunner.kt`
- Test: `core/process/src/jvmTest/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunnerStreamTest.kt`

**Interfaces:**
- Consumes: Task 6의 `ProcessCommandRunner`, `reapTree`
- Produces: 동작하는 `stream(command, capacity): Flow<CommandEvent>`

- [ ] **Step 1: 실패하는 테스트 작성**

`core/process/src/jvmTest/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunnerStreamTest.kt`:

```kotlin
package dev.citytexi.simulcast.process

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProcessCommandRunnerStreamTest {

    private val runner = ProcessCommandRunner()

    @Test
    fun emits_lines_then_exit_code() = runTest {
        val script = "echo one; echo two; echo oops 1>&2; exit 3"
        val events = runner.stream(Command("/bin/sh", listOf("-c", script))).toList()

        assertEquals(
            listOf("one", "two"),
            events.filterIsInstance<CommandEvent.Stdout>().map { it.line },
        )
        assertEquals(
            listOf("oops"),
            events.filterIsInstance<CommandEvent.Stderr>().map { it.line },
        )
        assertEquals(CommandEvent.Exited(3), events.last())
    }

    @Test
    fun emits_start_failure_instead_of_throwing() = runTest {
        val events = runner.stream(Command("/nonexistent/tool")).toList()

        assertTrue(events.size == 1)
        assertIs<CommandEvent.StartFailed>(events.single())
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :core:process:jvmTest --tests '*ProcessCommandRunnerStreamTest*'
```

Expected: FAIL — `stream`이 빈 flow라 이벤트가 0개다.

- [ ] **Step 3: `stream` 구현**

`ProcessCommandRunner`의 `stream`을 교체:

```kotlin
    override fun stream(command: Command, capacity: Int): Flow<CommandEvent> = callbackFlow {
        val process = try {
            command.toProcessBuilder().start()
        } catch (e: IOException) {
            send(CommandEvent.StartFailed(e.message ?: e.toString()))
            close()
            return@callbackFlow
        }

        val readers = listOf(
            launch(io) {
                process.inputStream.bufferedReader().forEachLine { trySend(CommandEvent.Stdout(it)) }
            },
            launch(io) {
                process.errorStream.bufferedReader().forEachLine { trySend(CommandEvent.Stderr(it)) }
            },
        )

        launch(io) {
            val exitCode = process.waitFor()
            readers.joinAll()
            trySend(CommandEvent.Exited(exitCode))
            close()
        }

        awaitClose { process.reapTree() }
    }.buffer(capacity, BufferOverflow.SUSPEND)
```

import 추가:

```kotlin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :core:process:jvmTest --tests '*ProcessCommandRunnerStreamTest*'
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add core/process
git commit -m "feat(process): stream stdout, stderr, and the exit code as events"
```

---

### Task 9: 취소 시 손자 프로세스 회수

`stream`은 logcat처럼 끝나지 않는 명령을 켜고 끄는 것이 상시 동작이다. 직계만 죽이면 `adb shell`·`simctl spawn`이 만든 손자가 남아 앱을 닫은 뒤에도 쌓인다.

**Files:**
- Test: `core/process/src/jvmTest/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunnerReapTest.kt`

**Interfaces:**
- Consumes: Task 8의 `stream`, Task 6의 `reapTree`
- Produces: 없음 (테스트)

- [ ] **Step 1: 테스트 작성**

셸이 손자를 만들고 그 pid를 stdout으로 알려 준다. 수집을 끊은 뒤 그 pid가 죽었는지 본다.

`core/process/src/jvmTest/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunnerReapTest.kt`:

```kotlin
package dev.citytexi.simulcast.process

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class ProcessCommandRunnerReapTest {

    @Test
    fun kills_grandchildren_when_collection_is_cancelled() = runBlocking {
        val runner = ProcessCommandRunner()
        val script = "sleep 120 & echo \$!; wait"

        val grandchildPid = withTimeout(10.seconds) {
            runner.stream(Command("/bin/sh", listOf("-c", script)))
                .filterIsInstance<CommandEvent.Stdout>()
                .first()
                .line
                .trim()
                .toLong()
        }

        // first() 가 수집을 끊었으므로 awaitClose 의 회수가 이미 돌았다.
        Thread.sleep(1_000)

        val alive = ProcessHandle.of(grandchildPid).map { it.isAlive }.orElse(false)
        assertFalse(alive, "손자 프로세스 $grandchildPid 가 살아남았다")
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :core:process:jvmTest --tests '*ProcessCommandRunnerReapTest*'
```

Expected: PASS. 실패하면 `reapTree`가 자손 목록을 루트보다 먼저 붙잡지 않은 것이다.

- [ ] **Step 3: 커밋**

```bash
git add core/process
git commit -m "test(process): pin descendant reaping on stream cancellation"
```

---

### Task 10: 스트림 유실을 조용하지 않게

`callbackFlow`의 채널이 차면 `trySend`가 실패하고 그 줄은 경고 없이 사라진다. logcat은 초당 수천 줄을 뿜고 로그 뷰어가 이 앱의 핵심 가치이므로, 버린 줄 수를 `Dropped`로 알린다. 드롭 계산은 순수 클래스로 떼어 결정적으로 테스트한다 — 실제 프로세스로 오버플로를 재현하면 타이밍에 기대는 불안정한 테스트가 된다.

**Files:**
- Create: `core/process/src/commonMain/kotlin/dev/citytexi/simulcast/process/DropCountingSink.kt`
- Modify: `core/process/src/jvmMain/kotlin/dev/citytexi/simulcast/process/ProcessCommandRunner.kt`
- Test: `core/process/src/commonTest/kotlin/dev/citytexi/simulcast/process/DropCountingSinkTest.kt`

**Interfaces:**
- Consumes: Task 8의 `stream`
- Produces: `internal class DropCountingSink(send: (CommandEvent) -> Boolean)` with `fun offer(event: CommandEvent)`

- [ ] **Step 1: 실패하는 테스트 작성**

`core/process/src/commonTest/kotlin/dev/citytexi/simulcast/process/DropCountingSinkTest.kt`:

```kotlin
package dev.citytexi.simulcast.process

import kotlin.test.Test
import kotlin.test.assertEquals

class DropCountingSinkTest {

    @Test
    fun reports_dropped_count_once_the_channel_accepts_again() {
        val accepted = mutableListOf<CommandEvent>()
        var open = false
        val sink = DropCountingSink { event ->
            if (open) accepted += event
            open
        }

        sink.offer(CommandEvent.Stdout("a"))
        sink.offer(CommandEvent.Stdout("b"))
        open = true
        sink.offer(CommandEvent.Stdout("c"))

        assertEquals(
            listOf(CommandEvent.Dropped(2), CommandEvent.Stdout("c")),
            accepted,
        )
    }

    @Test
    fun stays_quiet_when_nothing_is_dropped() {
        val accepted = mutableListOf<CommandEvent>()
        val sink = DropCountingSink { accepted += it; true }

        sink.offer(CommandEvent.Stdout("a"))

        assertEquals(listOf(CommandEvent.Stdout("a")), accepted)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :core:process:jvmTest --tests '*DropCountingSinkTest*'
```

Expected: FAIL — `Unresolved reference: DropCountingSink`

- [ ] **Step 3: 구현 작성**

`core/process/src/commonMain/kotlin/dev/citytexi/simulcast/process/DropCountingSink.kt`:

```kotlin
package dev.citytexi.simulcast.process

/**
 * @param send 채널이 받아들였으면 true. 실패한 이벤트는 버려진 것으로 센다.
 */
internal class DropCountingSink(private val send: (CommandEvent) -> Boolean) {

    private var dropped = 0

    fun offer(event: CommandEvent) {
        if (dropped > 0 && send(CommandEvent.Dropped(dropped))) {
            dropped = 0
        }
        if (!send(event)) {
            dropped++
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :core:process:jvmTest --tests '*DropCountingSinkTest*'
```

Expected: PASS

- [ ] **Step 5: `stream`에 배선**

`stream` 안에서 `trySend`를 직접 부르던 자리를 sink 경유로 바꾼다. `callbackFlow` 블록 첫 줄에 추가:

```kotlin
        val sink = DropCountingSink { trySend(it).isSuccess }
```

그리고 세 곳의 `trySend(...)`를 각각 `sink.offer(...)`로 교체한다:

```kotlin
        val readers = listOf(
            launch(io) {
                process.inputStream.bufferedReader().forEachLine { sink.offer(CommandEvent.Stdout(it)) }
            },
            launch(io) {
                process.errorStream.bufferedReader().forEachLine { sink.offer(CommandEvent.Stderr(it)) }
            },
        )

        launch(io) {
            val exitCode = process.waitFor()
            readers.joinAll()
            sink.offer(CommandEvent.Exited(exitCode))
            close()
        }
```

- [ ] **Step 6: 전체 테스트 통과 확인**

```bash
./gradlew :core:process:jvmTest
```

Expected: PASS (Task 4~10의 모든 테스트)

- [ ] **Step 7: 커밋**

```bash
git add core/process
git commit -m "feat(process): report dropped lines instead of losing them silently"
```

---

# 페이즈 C — 수직 슬라이스

### Task 11: `core:common` Outcome

`kotlin.Result`를 쓰지 않는 이유는 실패 슬롯이 `Throwable`이기 때문이다. `DeviceError`를 담으려면 그것을 `Throwable`로 만들어야 하고, 그러면 `CancellationException` 재던지기 규칙과 섞여 위험해진다.

스펙은 `core:common`의 책임으로 디스패처 추상화도 적었지만 이 라운드에서는 만들지 않는다. `ProcessCommandRunner`가 디스패처를 생성자로 받고 나머지 계층은 호출자의 컨텍스트를 그대로 쓰므로 지금 쓸 자리가 없다. 필요해지는 시점에 추가한다.

**Files:**
- Create: `core/common/src/commonMain/kotlin/dev/citytexi/simulcast/common/Outcome.kt`
- Test: `core/common/src/commonTest/kotlin/dev/citytexi/simulcast/common/OutcomeTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `Outcome.Ok<T>(value)`, `Outcome.Err<E>(error)`, `Outcome<T, E>.map(transform)`, `Outcome<T, E>.valueOrNull()`

- [ ] **Step 1: 실패하는 테스트 작성**

`core/common/src/commonTest/kotlin/dev/citytexi/simulcast/common/OutcomeTest.kt`:

```kotlin
package dev.citytexi.simulcast.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutcomeTest {

    @Test
    fun map_transforms_only_the_success_side() {
        val ok: Outcome<Int, String> = Outcome.Ok(2)
        val err: Outcome<Int, String> = Outcome.Err("boom")

        assertEquals(Outcome.Ok(4), ok.map { it * 2 })
        assertEquals(Outcome.Err("boom"), err.map { it * 2 })
    }

    @Test
    fun value_or_null_returns_null_on_failure() {
        val err: Outcome<Int, String> = Outcome.Err("boom")

        assertEquals(2, Outcome.Ok(2).valueOrNull())
        assertNull(err.valueOrNull())
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :core:common:jvmTest
```

Expected: FAIL — `Unresolved reference: Outcome`

- [ ] **Step 3: 구현 작성**

`core/common/src/commonMain/kotlin/dev/citytexi/simulcast/common/Outcome.kt`:

```kotlin
package dev.citytexi.simulcast.common

sealed interface Outcome<out T, out E> {
    data class Ok<out T>(val value: T) : Outcome<T, Nothing>
    data class Err<out E>(val error: E) : Outcome<Nothing, E>
}

inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

fun <T, E> Outcome<T, E>.valueOrNull(): T? = when (this) {
    is Outcome.Ok -> value
    is Outcome.Err -> null
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :core:common:jvmTest
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add core/common
git commit -m "feat(common): add Outcome, the failure type that can hold a non-Throwable error"
```

---

### Task 12: domain 모델 · 에러 · 인터페이스

**Files:**
- Create: `domain/build.gradle.kts`
- Create: `domain/src/commonMain/kotlin/dev/citytexi/simulcast/domain/Device.kt`
- Create: `domain/src/commonMain/kotlin/dev/citytexi/simulcast/domain/DeviceError.kt`
- Create: `domain/src/commonMain/kotlin/dev/citytexi/simulcast/domain/DeviceRepository.kt`
- Create: `domain/src/commonMain/kotlin/dev/citytexi/simulcast/domain/GetDevicesUseCase.kt`
- Test: `domain/src/commonTest/kotlin/dev/citytexi/simulcast/domain/GetDevicesUseCaseTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: Task 11의 `Outcome`
- Produces: `DevicePlatform`, `DeviceState`, `Device(id, name, platform, state)`, `DeviceError.{ToolNotFound, ToolFailed, Timeout, ParseFailed}`, `DeviceListing(android, ios)`, `DeviceRepository.listDevices(): DeviceListing`, `GetDevicesUseCase(repository)` with `suspend operator fun invoke(): DeviceListing`

- [ ] **Step 1: 실패하는 테스트 작성**

`domain/src/commonTest/kotlin/dev/citytexi/simulcast/domain/GetDevicesUseCaseTest.kt`:

```kotlin
package dev.citytexi.simulcast.domain

import dev.citytexi.simulcast.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetDevicesUseCaseTest {

    @Test
    fun passes_the_listing_through() = runTest {
        val listing = DeviceListing(
            android = Outcome.Ok(listOf(Device("emulator-5554", "Pixel", DevicePlatform.ANDROID, DeviceState.RUNNING))),
            ios = Outcome.Err(DeviceError.ToolNotFound("xcrun")),
        )
        val useCase = GetDevicesUseCase(FakeDeviceRepository(listing))

        assertEquals(listing, useCase())
    }
}

private class FakeDeviceRepository(private val listing: DeviceListing) : DeviceRepository {
    override suspend fun listDevices(): DeviceListing = listing
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :domain:jvmTest
```

Expected: FAIL — `Project ':domain' not found`

- [ ] **Step 3: 모델 작성**

`domain/src/commonMain/kotlin/dev/citytexi/simulcast/domain/Device.kt`:

```kotlin
package dev.citytexi.simulcast.domain

enum class DevicePlatform { ANDROID, IOS }

/** adb 와 simctl 의 상태 어휘를 공통 축으로 접은 것. 매핑은 data 레이어가 한다. */
enum class DeviceState { RUNNING, STARTING, STOPPED, UNAVAILABLE }

/**
 * @param id 실행 중이면 adb serial 또는 simctl UDID, 정지 상태면 AVD 이름이다.
 */
data class Device(
    val id: String,
    val name: String,
    val platform: DevicePlatform,
    val state: DeviceState,
)
```

`domain/src/commonMain/kotlin/dev/citytexi/simulcast/domain/DeviceError.kt`:

```kotlin
package dev.citytexi.simulcast.domain

sealed interface DeviceError {
    data class ToolNotFound(val tool: String) : DeviceError
    data class ToolFailed(val tool: String, val exitCode: Int, val stderr: String) : DeviceError
    data class Timeout(val tool: String) : DeviceError
    data class ParseFailed(val tool: String, val detail: String) : DeviceError
}
```

`domain/src/commonMain/kotlin/dev/citytexi/simulcast/domain/DeviceRepository.kt`:

```kotlin
package dev.citytexi.simulcast.domain

import dev.citytexi.simulcast.common.Outcome

/** 한쪽 플랫폼 조회가 실패해도 다른 쪽 결과는 살아 있어야 하므로 갈래를 따로 든다. */
data class DeviceListing(
    val android: Outcome<List<Device>, DeviceError>,
    val ios: Outcome<List<Device>, DeviceError>,
)

interface DeviceRepository {
    suspend fun listDevices(): DeviceListing
}
```

`domain/src/commonMain/kotlin/dev/citytexi/simulcast/domain/GetDevicesUseCase.kt`:

```kotlin
package dev.citytexi.simulcast.domain

class GetDevicesUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(): DeviceListing = repository.listDevices()
}
```

- [ ] **Step 4: 모듈 등록**

`domain/build.gradle.kts`:

```kotlin
plugins {
    id("simulcast.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
        }
    }
}
```

`settings.gradle.kts`의 `include` 목록에 추가:

```kotlin
include(":domain")
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :domain:jvmTest
```

Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add domain settings.gradle.kts
git commit -m "feat(domain): add device model, error taxonomy, and the repository contract"
```

---

### Task 13: 실행 파일 탐색

macOS에서 Finder나 launchd로 실행된 GUI 앱은 셸 환경을 상속하지 않는다 — `~/.zshrc`의 `ANDROID_HOME`은 없고 PATH는 `/usr/bin:/bin:/usr/sbin:/sbin` 수준이다. `./gradlew run`에서는 되다가 dmg로 설치한 순간 도구를 못 찾는다. 그래서 PATH에 기대지 않고 순서를 고정해 찾는다.

**Files:**
- Create: `data/build.gradle.kts`
- Create: `data/src/commonMain/kotlin/dev/citytexi/simulcast/data/tool/ToolLocator.kt`
- Test: `data/src/commonTest/kotlin/dev/citytexi/simulcast/data/tool/ToolLocatorTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: 없음
- Produces: `ToolLocator(env: Map<String, String>, homeDir: String, exists: (String) -> Boolean)` with `fun adb(): String?`, `fun emulator(): String?`, `fun xcrun(): String?`

- [ ] **Step 1: 실패하는 테스트 작성**

`data/src/commonTest/kotlin/dev/citytexi/simulcast/data/tool/ToolLocatorTest.kt`:

```kotlin
package dev.citytexi.simulcast.data.tool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolLocatorTest {

    @Test
    fun prefers_the_sdk_root_from_the_environment() {
        val locator = ToolLocator(
            env = mapOf("ANDROID_HOME" to "/sdk"),
            homeDir = "/Users/someone",
            exists = { it == "/sdk/platform-tools/adb" },
        )

        assertEquals("/sdk/platform-tools/adb", locator.adb())
    }

    @Test
    fun falls_back_to_the_conventional_path_under_home() {
        val conventional = "/Users/someone/Library/Android/sdk/platform-tools/adb"
        val locator = ToolLocator(
            env = emptyMap(),
            homeDir = "/Users/someone",
            exists = { it == conventional },
        )

        assertEquals(conventional, locator.adb())
    }

    @Test
    fun returns_null_when_nothing_matches() {
        val locator = ToolLocator(env = emptyMap(), homeDir = "/Users/someone", exists = { false })

        assertNull(locator.adb())
        assertNull(locator.emulator())
        assertNull(locator.xcrun())
    }

    @Test
    fun finds_xcrun_at_its_fixed_location() {
        val locator = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { it == "/usr/bin/xcrun" })

        assertEquals("/usr/bin/xcrun", locator.xcrun())
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :data:jvmTest
```

Expected: FAIL — `Project ':data' not found`

- [ ] **Step 3: 구현 작성**

`data/src/commonMain/kotlin/dev/citytexi/simulcast/data/tool/ToolLocator.kt`:

```kotlin
package dev.citytexi.simulcast.data.tool

/**
 * @param exists 경로가 실제로 존재하는지. 테스트에서 파일 시스템을 대체할 수 있게 주입한다.
 */
class ToolLocator(
    private val env: Map<String, String>,
    private val homeDir: String,
    private val exists: (String) -> Boolean,
) {

    fun adb(): String? = firstExisting(androidSdkRoots().map { "$it/platform-tools/adb" })

    fun emulator(): String? = firstExisting(androidSdkRoots().map { "$it/emulator/emulator" })

    fun xcrun(): String? = firstExisting(listOf("/usr/bin/xcrun"))

    private fun androidSdkRoots(): List<String> = buildList {
        env["ANDROID_HOME"]?.let(::add)
        env["ANDROID_SDK_ROOT"]?.let(::add)
        add("$homeDir/Library/Android/sdk")
    }

    private fun firstExisting(candidates: List<String>): String? = candidates.firstOrNull(exists)
}
```

- [ ] **Step 4: 모듈 등록**

`data/build.gradle.kts`:

```kotlin
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
```

`settings.gradle.kts`의 `include` 목록에 추가:

```kotlin
include(":data")
```

- [ ] **Step 5: 누락된 컨벤션 플러그인 두 개 작성**

`build-logic/src/main/kotlin/simulcast.serialization.gradle.kts`:

```kotlin
plugins {
    id("simulcast.kmp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.lib("kotlinx-serialization-json"))
        }
    }
}
```

`build-logic/src/main/kotlin/simulcast.koin.gradle.kts`:

```kotlin
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
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew :data:jvmTest
```

Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add data build-logic settings.gradle.kts
git commit -m "feat(data): locate adb, emulator, and xcrun without relying on PATH"
```

---

### Task 14: `adb devices -l` 파서

**Files:**
- Create: `data/src/commonMain/kotlin/dev/citytexi/simulcast/data/android/AdbDevicesParser.kt`
- Test: `data/src/commonTest/kotlin/dev/citytexi/simulcast/data/android/AdbDevicesParserTest.kt`

**Interfaces:**
- Consumes: Task 12의 `DeviceState`
- Produces: `data class AdbEntry(val serial: String, val state: DeviceState)`, `fun parseAdbDevices(output: String): List<AdbEntry>`

- [ ] **Step 1: 실패하는 테스트 작성**

`data/src/commonTest/kotlin/dev/citytexi/simulcast/data/android/AdbDevicesParserTest.kt`:

```kotlin
package dev.citytexi.simulcast.data.android

import dev.citytexi.simulcast.domain.DeviceState
import kotlin.test.Test
import kotlin.test.assertEquals

class AdbDevicesParserTest {

    private val output = """
        List of devices attached
        emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 transport_id:1
        emulator-5556          offline transport_id:2
        1A2B3C4D               unauthorized transport_id:3
        R5CT10ABCDE            device product:a53x model:SM_A536N transport_id:4

    """.trimIndent()

    @Test
    fun maps_adb_states_to_the_common_vocabulary() {
        assertEquals(
            listOf(
                AdbEntry("emulator-5554", DeviceState.RUNNING),
                AdbEntry("emulator-5556", DeviceState.STARTING),
                AdbEntry("1A2B3C4D", DeviceState.UNAVAILABLE),
                AdbEntry("R5CT10ABCDE", DeviceState.RUNNING),
            ),
            parseAdbDevices(output),
        )
    }

    @Test
    fun returns_empty_when_nothing_is_attached() {
        assertEquals(emptyList(), parseAdbDevices("List of devices attached\n\n"))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :data:jvmTest --tests '*AdbDevicesParserTest*'
```

Expected: FAIL — `Unresolved reference: parseAdbDevices`

- [ ] **Step 3: 구현 작성**

`data/src/commonMain/kotlin/dev/citytexi/simulcast/data/android/AdbDevicesParser.kt`:

```kotlin
package dev.citytexi.simulcast.data.android

import dev.citytexi.simulcast.domain.DeviceState

data class AdbEntry(val serial: String, val state: DeviceState)

/**
 * `adb devices -l` 은 첫 줄이 헤더이고 그 뒤가 `<serial> <state> [key:value ...]` 다.
 * 목록에 없는 AVD 는 부팅되지 않은 것이라 여기서는 알 수 없다 — 그 축은 emulator 쪽이 채운다.
 */
fun parseAdbDevices(output: String): List<AdbEntry> =
    output.lineSequence()
        .drop(1)
        .map(String::trim)
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val columns = line.split(Regex("\\s+"))
            if (columns.size < 2) return@mapNotNull null
            AdbEntry(columns[0], columns[1].toDeviceState())
        }
        .toList()

private fun String.toDeviceState(): DeviceState = when (this) {
    "device" -> DeviceState.RUNNING
    "offline" -> DeviceState.STARTING
    else -> DeviceState.UNAVAILABLE
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :data:jvmTest --tests '*AdbDevicesParserTest*'
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add data
git commit -m "feat(data): parse adb devices output"
```

---

### Task 15: Android 소스 — AVD 목록과 실행 중인 것의 조인

`adb devices`는 연결된 것만 나열한다. 그것만 쓰면 정지된 AVD가 목록에서 통째로 빠지고 `DeviceState.STOPPED`가 Android 쪽에서 도달 불가능한 값이 된다. v0.2의 "양쪽 동시 실행"도 정지된 AVD를 띄울 수 있어야 성립한다.

**Files:**
- Create: `data/src/commonMain/kotlin/dev/citytexi/simulcast/data/android/AndroidDeviceSource.kt`
- Test: `data/src/commonTest/kotlin/dev/citytexi/simulcast/data/android/AndroidDeviceSourceTest.kt`
- Test: `data/src/commonTest/kotlin/dev/citytexi/simulcast/data/FakeCommandRunner.kt`

**Interfaces:**
- Consumes: Task 13의 `ToolLocator`, Task 14의 `parseAdbDevices`, `CommandRunner`(Task 4), `Outcome`(Task 11), `DeviceError`(Task 12)
- Produces: `AndroidDeviceSource(runner, locator)` with `suspend fun list(): Outcome<List<Device>, DeviceError>`

- [ ] **Step 1: 테스트 더블 작성**

`data/src/commonTest/kotlin/dev/citytexi/simulcast/data/FakeCommandRunner.kt`:

```kotlin
package dev.citytexi.simulcast.data

import dev.citytexi.simulcast.process.Command
import dev.citytexi.simulcast.process.CommandEvent
import dev.citytexi.simulcast.process.CommandResult
import dev.citytexi.simulcast.process.CommandRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration

/**
 * @param responses 인자 리스트 전체를 키로 한다. 명령 이름은 탐색 결과에 따라 달라지므로 키에 넣지 않는다.
 */
class FakeCommandRunner(
    private val responses: Map<List<String>, CommandResult>,
) : CommandRunner {

    val invoked = mutableListOf<Command>()

    override suspend fun run(command: Command, timeout: Duration): CommandResult {
        invoked += command
        return responses[command.args] ?: CommandResult.StartFailed("unstubbed: ${command.args}")
    }

    override fun stream(command: Command, capacity: Int): Flow<CommandEvent> = emptyFlow()
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`data/src/commonTest/kotlin/dev/citytexi/simulcast/data/android/AndroidDeviceSourceTest.kt`:

```kotlin
package dev.citytexi.simulcast.data.android

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.data.FakeCommandRunner
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceState
import dev.citytexi.simulcast.process.CommandResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidDeviceSourceTest {

    private val locator = ToolLocator(
        env = mapOf("ANDROID_HOME" to "/sdk"),
        homeDir = "/h",
        exists = { it.startsWith("/sdk") },
    )

    @Test
    fun joins_stopped_avds_with_running_emulators() = runTest {
        val runner = FakeCommandRunner(
            mapOf(
                listOf("-list-avds") to CommandResult.Completed(0, "Pixel_7\nPixel_Tablet\n", ""),
                listOf("devices", "-l") to CommandResult.Completed(
                    0,
                    "List of devices attached\nemulator-5554  device transport_id:1\n",
                    "",
                ),
                listOf("-s", "emulator-5554", "emu", "avd", "name") to
                    CommandResult.Completed(0, "Pixel_7\nOK\n", ""),
            ),
        )

        val result = AndroidDeviceSource(runner, locator).list()

        assertEquals(
            Outcome.Ok(
                listOf(
                    Device("emulator-5554", "Pixel_7", DevicePlatform.ANDROID, DeviceState.RUNNING),
                    Device("Pixel_Tablet", "Pixel_Tablet", DevicePlatform.ANDROID, DeviceState.STOPPED),
                )
            ),
            result,
        )
    }

    @Test
    fun reports_tool_not_found_when_adb_is_missing() = runTest {
        val emptyLocator = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { false })

        val result = AndroidDeviceSource(FakeCommandRunner(emptyMap()), emptyLocator).list()

        assertEquals(Outcome.Err(DeviceError.ToolNotFound("adb")), result)
    }

    @Test
    fun reports_tool_failed_when_adb_exits_nonzero() = runTest {
        val runner = FakeCommandRunner(
            mapOf(
                listOf("-list-avds") to CommandResult.Completed(0, "", ""),
                listOf("devices", "-l") to CommandResult.Completed(1, "", "adb: no permissions"),
            ),
        )

        val result = AndroidDeviceSource(runner, locator).list()

        assertEquals(Outcome.Err(DeviceError.ToolFailed("adb", 1, "adb: no permissions")), result)
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew :data:jvmTest --tests '*AndroidDeviceSourceTest*'
```

Expected: FAIL — `Unresolved reference: AndroidDeviceSource`

- [ ] **Step 4: 구현 작성**

`data/src/commonMain/kotlin/dev/citytexi/simulcast/data/android/AndroidDeviceSource.kt`:

```kotlin
package dev.citytexi.simulcast.data.android

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceState
import dev.citytexi.simulcast.process.Command
import dev.citytexi.simulcast.process.CommandResult
import dev.citytexi.simulcast.process.CommandRunner
import kotlin.time.Duration.Companion.seconds

class AndroidDeviceSource(
    private val runner: CommandRunner,
    private val locator: ToolLocator,
) {

    suspend fun list(): Outcome<List<Device>, DeviceError> {
        val adb = locator.adb() ?: return Outcome.Err(DeviceError.ToolNotFound("adb"))

        val avdNames = locator.emulator()
            ?.let { emulator -> runner.run(Command(emulator, listOf("-list-avds")), TIMEOUT) }
            ?.let { it as? CommandResult.Completed }
            ?.stdout
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter { it.isNotEmpty() }
            ?.toList()
            .orEmpty()

        val attached = when (val result = runner.run(Command(adb, listOf("devices", "-l")), TIMEOUT)) {
            is CommandResult.Completed ->
                if (result.exitCode == 0) parseAdbDevices(result.stdout)
                else return Outcome.Err(DeviceError.ToolFailed("adb", result.exitCode, result.stderr.trim()))
            is CommandResult.TimedOut -> return Outcome.Err(DeviceError.Timeout("adb"))
            is CommandResult.StartFailed -> return Outcome.Err(DeviceError.ToolNotFound("adb"))
        }

        val running = attached.map { entry ->
            val avdName = avdNameOf(adb, entry.serial)
            Device(entry.serial, avdName ?: entry.serial, DevicePlatform.ANDROID, entry.state)
        }
        val runningAvdNames = running.mapNotNull { it.name }.toSet()
        val stopped = avdNames
            .filterNot { it in runningAvdNames }
            .map { Device(it, it, DevicePlatform.ANDROID, DeviceState.STOPPED) }

        return Outcome.Ok(running + stopped)
    }

    /** 실물 기기는 AVD 이름이 없다. 그 경우 null 이고 호출부가 serial 로 대신한다. */
    private suspend fun avdNameOf(adb: String, serial: String): String? {
        val result = runner.run(Command(adb, listOf("-s", serial, "emu", "avd", "name")), TIMEOUT)
        val stdout = (result as? CommandResult.Completed)?.takeIf { it.exitCode == 0 }?.stdout ?: return null
        return stdout.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() && it != "OK" }
    }

    private companion object {
        val TIMEOUT = 10.seconds
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :data:jvmTest --tests '*AndroidDeviceSourceTest*'
```

Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add data
git commit -m "feat(data): list android devices by joining avd names with attached emulators"
```

---

### Task 16: iOS 소스 — `simctl list --json` 파싱과 필터

`--json`은 watchOS·tvOS·visionOS 런타임까지 전부 뱉고 `isAvailable: false`인 조합도 섞여 있다. 거르지 않으면 목록이 수십 개로 부풀고 그중 대부분이 띄울 수 없는 항목이다.

**Files:**
- Create: `data/src/commonMain/kotlin/dev/citytexi/simulcast/data/ios/SimctlJson.kt`
- Create: `data/src/commonMain/kotlin/dev/citytexi/simulcast/data/ios/IosDeviceSource.kt`
- Test: `data/src/commonTest/kotlin/dev/citytexi/simulcast/data/ios/IosDeviceSourceTest.kt`

**Interfaces:**
- Consumes: Task 13의 `ToolLocator`, `CommandRunner`, `Outcome`, `DeviceError`
- Produces: `fun parseSimctlDevices(json: String): List<Device>`, `IosDeviceSource(runner, locator)` with `suspend fun list(): Outcome<List<Device>, DeviceError>`

- [ ] **Step 1: 실패하는 테스트 작성**

`data/src/commonTest/kotlin/dev/citytexi/simulcast/data/ios/IosDeviceSourceTest.kt`:

```kotlin
package dev.citytexi.simulcast.data.ios

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.data.FakeCommandRunner
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceState
import dev.citytexi.simulcast.process.CommandResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IosDeviceSourceTest {

    private val locator = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { it == "/usr/bin/xcrun" })

    private val json = """
        {
          "devices": {
            "com.apple.CoreSimulator.SimRuntime.iOS-18-2": [
              { "udid": "AAA", "name": "iPhone 16", "state": "Booted", "isAvailable": true },
              { "udid": "BBB", "name": "iPhone 16 Pro", "state": "Shutdown", "isAvailable": true },
              { "udid": "CCC", "name": "iPhone SE", "state": "Shutdown", "isAvailable": false }
            ],
            "com.apple.CoreSimulator.SimRuntime.watchOS-11-2": [
              { "udid": "DDD", "name": "Apple Watch", "state": "Shutdown", "isAvailable": true }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun keeps_only_available_ios_runtimes() = runTest {
        val runner = FakeCommandRunner(
            mapOf(listOf("simctl", "list", "devices", "--json") to CommandResult.Completed(0, json, "")),
        )

        val result = IosDeviceSource(runner, locator).list()

        assertEquals(
            Outcome.Ok(
                listOf(
                    Device("AAA", "iPhone 16", DevicePlatform.IOS, DeviceState.RUNNING),
                    Device("BBB", "iPhone 16 Pro", DevicePlatform.IOS, DeviceState.STOPPED),
                )
            ),
            result,
        )
    }

    @Test
    fun reports_parse_failure_on_unreadable_output() = runTest {
        val runner = FakeCommandRunner(
            mapOf(listOf("simctl", "list", "devices", "--json") to CommandResult.Completed(0, "not json", "")),
        )

        val result = IosDeviceSource(runner, locator).list()

        assertIs<Outcome.Err<DeviceError.ParseFailed>>(result)
    }

    @Test
    fun reports_tool_not_found_when_xcrun_is_missing() = runTest {
        val emptyLocator = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { false })

        val result = IosDeviceSource(FakeCommandRunner(emptyMap()), emptyLocator).list()

        assertEquals(Outcome.Err(DeviceError.ToolNotFound("xcrun")), result)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :data:jvmTest --tests '*IosDeviceSourceTest*'
```

Expected: FAIL — `Unresolved reference: IosDeviceSource`

- [ ] **Step 3: JSON 모델과 파서 작성**

`data/src/commonMain/kotlin/dev/citytexi/simulcast/data/ios/SimctlJson.kt`:

```kotlin
package dev.citytexi.simulcast.data.ios

import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SimctlList(val devices: Map<String, List<SimctlDevice>> = emptyMap())

@Serializable
private data class SimctlDevice(
    val udid: String,
    val name: String,
    val state: String,
    val isAvailable: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }

/** 런타임 키가 iOS 인 것, 그리고 실제로 띄울 수 있는 것만 남긴다. */
fun parseSimctlDevices(raw: String): List<Device> =
    json.decodeFromString<SimctlList>(raw)
        .devices
        .filterKeys { it.contains("SimRuntime.iOS") }
        .values
        .flatten()
        .filter { it.isAvailable }
        .map { Device(it.udid, it.name, DevicePlatform.IOS, it.state.toDeviceState()) }

private fun String.toDeviceState(): DeviceState = when (this) {
    "Booted" -> DeviceState.RUNNING
    "Booting", "Shutting Down" -> DeviceState.STARTING
    "Shutdown" -> DeviceState.STOPPED
    else -> DeviceState.UNAVAILABLE
}
```

- [ ] **Step 4: 소스 작성**

`data/src/commonMain/kotlin/dev/citytexi/simulcast/data/ios/IosDeviceSource.kt`:

```kotlin
package dev.citytexi.simulcast.data.ios

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.process.Command
import dev.citytexi.simulcast.process.CommandResult
import dev.citytexi.simulcast.process.CommandRunner
import kotlin.time.Duration.Companion.seconds

class IosDeviceSource(
    private val runner: CommandRunner,
    private val locator: ToolLocator,
) {

    suspend fun list(): Outcome<List<Device>, DeviceError> {
        val xcrun = locator.xcrun() ?: return Outcome.Err(DeviceError.ToolNotFound("xcrun"))

        val command = Command(xcrun, listOf("simctl", "list", "devices", "--json"))
        return when (val result = runner.run(command, TIMEOUT)) {
            is CommandResult.Completed ->
                if (result.exitCode != 0) {
                    Outcome.Err(DeviceError.ToolFailed("xcrun", result.exitCode, result.stderr.trim()))
                } else {
                    runCatching { parseSimctlDevices(result.stdout) }
                        .fold(
                            onSuccess = { Outcome.Ok(it) },
                            onFailure = { Outcome.Err(DeviceError.ParseFailed("simctl", it.message ?: "")) },
                        )
                }
            is CommandResult.TimedOut -> Outcome.Err(DeviceError.Timeout("xcrun"))
            is CommandResult.StartFailed -> Outcome.Err(DeviceError.ToolNotFound("xcrun"))
        }
    }

    private companion object {
        val TIMEOUT = 20.seconds
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :data:jvmTest --tests '*IosDeviceSourceTest*'
```

Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add data
git commit -m "feat(data): list ios simulators, filtering to available ios runtimes"
```

---

### Task 17: Repository — 부분 성공

Xcode가 없는 기계에서 `xcrun`을 못 찾았다고 Android 목록까지 못 보면 안 된다.

**Files:**
- Create: `data/src/commonMain/kotlin/dev/citytexi/simulcast/data/DeviceRepositoryImpl.kt`
- Create: `data/src/jvmMain/kotlin/dev/citytexi/simulcast/data/DataModule.kt`
- Test: `data/src/commonTest/kotlin/dev/citytexi/simulcast/data/DeviceRepositoryImplTest.kt`

**Interfaces:**
- Consumes: Task 15의 `AndroidDeviceSource`, Task 16의 `IosDeviceSource`, Task 12의 `DeviceRepository`
- Produces: `DeviceRepositoryImpl(android, ios)`, `val dataModule: org.koin.core.module.Module`

- [ ] **Step 1: 실패하는 테스트 작성**

`data/src/commonTest/kotlin/dev/citytexi/simulcast/data/DeviceRepositoryImplTest.kt`:

```kotlin
package dev.citytexi.simulcast.data

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.data.android.AndroidDeviceSource
import dev.citytexi.simulcast.data.ios.IosDeviceSource
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.process.CommandResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeviceRepositoryImplTest {

    @Test
    fun a_missing_xcrun_does_not_hide_android_devices() = runTest {
        val androidLocator = ToolLocator(
            env = mapOf("ANDROID_HOME" to "/sdk"),
            homeDir = "/h",
            exists = { it.startsWith("/sdk") },
        )
        val noXcrun = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { false })
        val runner = FakeCommandRunner(
            mapOf(
                listOf("-list-avds") to CommandResult.Completed(0, "Pixel_7\n", ""),
                listOf("devices", "-l") to CommandResult.Completed(0, "List of devices attached\n", ""),
            ),
        )

        val listing = DeviceRepositoryImpl(
            android = AndroidDeviceSource(runner, androidLocator),
            ios = IosDeviceSource(runner, noXcrun),
        ).listDevices()

        val android = assertIs<Outcome.Ok<*>>(listing.android)
        assertEquals(1, (android.value as List<*>).size)
        assertEquals(Outcome.Err(DeviceError.ToolNotFound("xcrun")), listing.ios)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :data:jvmTest --tests '*DeviceRepositoryImplTest*'
```

Expected: FAIL — `Unresolved reference: DeviceRepositoryImpl`

- [ ] **Step 3: 구현 작성**

`data/src/commonMain/kotlin/dev/citytexi/simulcast/data/DeviceRepositoryImpl.kt`:

```kotlin
package dev.citytexi.simulcast.data

import dev.citytexi.simulcast.data.android.AndroidDeviceSource
import dev.citytexi.simulcast.data.ios.IosDeviceSource
import dev.citytexi.simulcast.domain.DeviceListing
import dev.citytexi.simulcast.domain.DeviceRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class DeviceRepositoryImpl(
    private val android: AndroidDeviceSource,
    private val ios: IosDeviceSource,
) : DeviceRepository {

    override suspend fun listDevices(): DeviceListing = coroutineScope {
        val androidResult = async { android.list() }
        val iosResult = async { ios.list() }
        DeviceListing(androidResult.await(), iosResult.await())
    }
}
```

- [ ] **Step 4: Koin 모듈 작성**

`data/src/jvmMain/kotlin/dev/citytexi/simulcast/data/DataModule.kt`. `System.getenv()`와 `java.io.File`은 JVM API라 `commonMain`에서는 해석되지 않는다 — jvm 타깃이 하나뿐이어도 `commonMain`은 공통 stdlib로만 컴파일된다.

```kotlin
package dev.citytexi.simulcast.data

import dev.citytexi.simulcast.data.android.AndroidDeviceSource
import dev.citytexi.simulcast.data.ios.IosDeviceSource
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.DeviceRepository
import org.koin.dsl.module

val dataModule = module {
    single { ToolLocator(env = System.getenv(), homeDir = System.getProperty("user.home"), exists = { java.io.File(it).canExecute() }) }
    single { AndroidDeviceSource(get(), get()) }
    single { IosDeviceSource(get(), get()) }
    single<DeviceRepository> { DeviceRepositoryImpl(get(), get()) }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :data:jvmTest
```

Expected: PASS (Task 13~17의 모든 테스트)

- [ ] **Step 6: 커밋**

```bash
git add data
git commit -m "feat(data): combine both platforms so one failure does not hide the other"
```

---

### Task 18: `feature:devices` 상태 홀더

**Files:**
- Create: `build-logic/src/main/kotlin/simulcast.feature.gradle.kts`
- Create: `feature/devices/build.gradle.kts`
- Create: `feature/devices/src/commonMain/kotlin/dev/citytexi/simulcast/feature/devices/DeviceListState.kt`
- Create: `feature/devices/src/commonMain/kotlin/dev/citytexi/simulcast/feature/devices/DeviceListViewModel.kt`
- Create: `feature/devices/src/commonMain/kotlin/dev/citytexi/simulcast/feature/devices/DevicesModule.kt`
- Test: `feature/devices/src/commonTest/kotlin/dev/citytexi/simulcast/feature/devices/DeviceListViewModelTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: Task 12의 `GetDevicesUseCase`, `DeviceListing`, `DeviceError`
- Produces: 플러그인 id `simulcast.feature`. `DeviceListState(loading, android, ios)`, `DeviceListViewModel(getDevices)` with `fun refresh()`, `val devicesModule: Module`

- [ ] **Step 1: `simulcast.feature` 컨벤션 플러그인 작성**

`feature`가 `data`를 참조하지 않는다는 규칙을 여기서 물리적으로 강제한다 — 자동 주입되는 의존에 `data`가 없으므로, 참조하려면 모듈 build 파일에 직접 적어야 하고 그것이 리뷰에서 눈에 띈다.

`build-logic/src/main/kotlin/simulcast.feature.gradle.kts`:

```kotlin
plugins {
    id("simulcast.compose")
    id("simulcast.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation(project(":core:designsystem"))
            implementation(libs.lib("orbit-core"))
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
```

- [ ] **Step 2: 실패하는 테스트 작성**

`orbit-test`의 API는 Orbit 메이저 버전마다 바뀐 이력이 있다. 아래가 컴파일되지 않으면 `orbit-test` 12.0.0 아티팩트의 KDoc을 확인하고 같은 의미의 호출로 바꾼다 — 검증하려는 것은 "refresh가 loading true를 거쳐 결과를 싣는다"이다.

`feature/devices/src/commonTest/kotlin/dev/citytexi/simulcast/feature/devices/DeviceListViewModelTest.kt`:

```kotlin
package dev.citytexi.simulcast.feature.devices

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.domain.DeviceListing
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceRepository
import dev.citytexi.simulcast.domain.DeviceState
import dev.citytexi.simulcast.domain.GetDevicesUseCase
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.test
import kotlin.test.Test

class DeviceListViewModelTest {

    private val listing = DeviceListing(
        android = Outcome.Ok(listOf(Device("emulator-5554", "Pixel_7", DevicePlatform.ANDROID, DeviceState.RUNNING))),
        ios = Outcome.Err(DeviceError.ToolNotFound("xcrun")),
    )

    @Test
    fun refresh_shows_loading_then_carries_both_sides() = runTest {
        val viewModel = DeviceListViewModel(GetDevicesUseCase(FakeRepository(listing)))

        viewModel.test(this) {
            expectInitialState()
            containerHost.refresh()
            expectState { copy(loading = true) }
            expectState { copy(loading = false, android = listing.android, ios = listing.ios) }
        }
    }
}

private class FakeRepository(private val listing: DeviceListing) : DeviceRepository {
    override suspend fun listDevices(): DeviceListing = listing
}
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew :feature:devices:jvmTest
```

Expected: FAIL — `Project ':feature:devices' not found`

- [ ] **Step 4: 상태와 상태 홀더 작성**

`feature/devices/src/commonMain/kotlin/dev/citytexi/simulcast/feature/devices/DeviceListState.kt`:

```kotlin
package dev.citytexi.simulcast.feature.devices

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError

/** null 은 아직 조회하지 않았다는 뜻이다. 조회 결과가 0건인 것과 구분해야 빈 화면 문구가 어긋나지 않는다. */
data class DeviceListState(
    val loading: Boolean = false,
    val android: Outcome<List<Device>, DeviceError>? = null,
    val ios: Outcome<List<Device>, DeviceError>? = null,
)
```

`feature/devices/src/commonMain/kotlin/dev/citytexi/simulcast/feature/devices/DeviceListViewModel.kt`:

```kotlin
package dev.citytexi.simulcast.feature.devices

import androidx.lifecycle.ViewModel
import dev.citytexi.simulcast.domain.GetDevicesUseCase
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.syntax.simple.intent
import org.orbitmvi.orbit.syntax.simple.reduce
import org.orbitmvi.orbit.viewmodel.container

class DeviceListViewModel(
    private val getDevices: GetDevicesUseCase,
) : ViewModel(), ContainerHost<DeviceListState, Nothing> {

    override val container = container<DeviceListState, Nothing>(DeviceListState())

    fun refresh() = intent {
        reduce { state.copy(loading = true) }
        val listing = getDevices()
        reduce { state.copy(loading = false, android = listing.android, ios = listing.ios) }
    }
}
```

`feature/devices/src/commonMain/kotlin/dev/citytexi/simulcast/feature/devices/DevicesModule.kt`:

```kotlin
package dev.citytexi.simulcast.feature.devices

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val devicesModule = module {
    viewModelOf(::DeviceListViewModel)
}
```

- [ ] **Step 5: 모듈 등록**

`feature/devices/build.gradle.kts`:

```kotlin
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
```

`settings.gradle.kts`의 `include` 목록에 추가:

```kotlin
include(":feature:devices")
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew :feature:devices:jvmTest
```

Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add feature build-logic settings.gradle.kts
git commit -m "feat(devices): add the device list state holder"
```

---

### Task 19: `feature:devices` 화면

**Files:**
- Create: `feature/devices/src/commonMain/kotlin/dev/citytexi/simulcast/feature/devices/DeviceListScreen.kt`

**Interfaces:**
- Consumes: Task 18의 `DeviceListViewModel`, `DeviceListState`
- Produces: `@Composable fun DeviceListScreen(viewModel: DeviceListViewModel = koinViewModel())`

- [ ] **Step 1: 화면 작성**

`feature/devices/src/commonMain/kotlin/dev/citytexi/simulcast/feature/devices/DeviceListScreen.kt`:

```kotlin
package dev.citytexi.simulcast.feature.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun DeviceListScreen(viewModel: DeviceListViewModel = koinViewModel()) {
    val state by viewModel.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = viewModel::refresh) { Text("새로고침") }
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxSize()) {
            DeviceColumn("Android", state.android, Modifier.weight(1f))
            DeviceColumn("iOS", state.ios, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeviceColumn(
    title: String,
    outcome: Outcome<List<Device>, DeviceError>?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(8.dp)) {
        Text(title)
        when (outcome) {
            null -> Text("조회 전")
            is Outcome.Err -> Text(outcome.error.describe())
            is Outcome.Ok ->
                if (outcome.value.isEmpty()) {
                    Text("없음")
                } else {
                    LazyColumn {
                        items(outcome.value) { device ->
                            Text("${device.name} · ${device.state}")
                        }
                    }
                }
        }
    }
}

private fun DeviceError.describe(): String = when (this) {
    is DeviceError.ToolNotFound -> "$tool 을 찾지 못했다"
    is DeviceError.ToolFailed -> "$tool 실패 (exit $exitCode): $stderr"
    is DeviceError.Timeout -> "$tool 응답이 없다"
    is DeviceError.ParseFailed -> "$tool 출력을 읽지 못했다: $detail"
}
```

`import androidx.compose.runtime.getValue`도 필요하다 — `by` 위임이 그것을 요구한다.

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew :feature:devices:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add feature
git commit -m "feat(devices): add the two-column device list screen"
```

---

### Task 20: app 조립

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/jvmMain/kotlin/dev/citytexi/simulcast/Main.kt`
- Create: `app/src/jvmMain/kotlin/dev/citytexi/simulcast/AppModules.kt`
- Test: `app/src/jvmTest/kotlin/dev/citytexi/simulcast/KoinGraphTest.kt`

**Interfaces:**
- Consumes: Task 17의 `dataModule`, Task 18의 `devicesModule`·`DeviceListViewModel`, Task 19의 `DeviceListScreen`
- Produces: `val appModules: List<Module>`

- [ ] **Step 1: 실패하는 테스트 작성**

Koin의 `verify()`는 **모듈 단위**로 돌면 실패한다 — `devicesModule`이 요구하는 `GetDevicesUseCase`의 바인딩은 `appModules`가 합쳐졌을 때만 존재한다. 그래서 합성 그래프를 실제로 시작해 최상위 진입점을 해석한다.

`app/src/jvmTest/kotlin/dev/citytexi/simulcast/KoinGraphTest.kt`:

```kotlin
package dev.citytexi.simulcast

import dev.citytexi.simulcast.feature.devices.DeviceListViewModel
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

class KoinGraphTest {

    @Test
    fun composed_graph_resolves_every_entry_point() {
        val koin = koinApplication { modules(appModules) }.koin

        assertNotNull(koin.get<DeviceListViewModel>())
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :app:jvmTest
```

Expected: FAIL — `Unresolved reference: appModules`

- [ ] **Step 3: 모듈 목록 작성**

`app/src/jvmMain/kotlin/dev/citytexi/simulcast/AppModules.kt`:

```kotlin
package dev.citytexi.simulcast

import dev.citytexi.simulcast.data.dataModule
import dev.citytexi.simulcast.domain.GetDevicesUseCase
import dev.citytexi.simulcast.feature.devices.devicesModule
import dev.citytexi.simulcast.process.CommandRunner
import dev.citytexi.simulcast.process.ProcessCommandRunner
import org.koin.core.module.Module
import org.koin.dsl.module

private val platformModule = module {
    single<CommandRunner> { ProcessCommandRunner() }
    factory { GetDevicesUseCase(get()) }
}

val appModules: List<Module> = listOf(platformModule, dataModule, devicesModule)
```

- [ ] **Step 4: app 의존 추가**

`app/build.gradle.kts`의 `jvmMain.dependencies`에 추가:

```kotlin
            implementation(project(":core:process"))
            implementation(project(":data"))
            implementation(project(":domain"))
            implementation(project(":feature:devices"))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
```

그리고 `jvmTest.dependencies` 블록을 추가:

```kotlin
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :app:jvmTest
```

Expected: PASS

- [ ] **Step 6: 화면 연결**

`app/src/jvmMain/kotlin/dev/citytexi/simulcast/Main.kt`를 교체:

```kotlin
package dev.citytexi.simulcast

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.citytexi.simulcast.designsystem.AppTheme
import dev.citytexi.simulcast.feature.devices.DeviceListScreen
import org.koin.compose.KoinApplication

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "cmp-simulcast") {
        KoinApplication(application = { modules(appModules) }) {
            AppTheme {
                DeviceListScreen()
            }
        }
    }
}
```

- [ ] **Step 7: 실제 실행 확인**

```bash
./gradlew :app:run
```

Expected: 창이 뜨고 좌측에 Android 디바이스 목록(정지된 AVD 포함), 우측에 iOS 시뮬레이터 목록이 보인다. Xcode가 없는 기계라면 우측에 "xcrun 을 찾지 못했다"가 뜨고 **좌측은 정상으로 보인다** — 그 비대칭이 부분 성공이 동작한다는 증거다.

- [ ] **Step 8: dmg에서도 도구를 찾는지 확인**

```bash
./gradlew :app:packageDmg
```

dmg를 열어 `.app`을 실행한다. Expected: `./gradlew run`과 같은 목록이 보인다. 여기서 "adb 를 찾지 못했다"가 뜨면 `ToolLocator`의 관례 경로 폴백이 동작하지 않은 것이다 — GUI 앱은 셸 환경을 상속하지 않으므로 `ANDROID_HOME`에 기댈 수 없다.

- [ ] **Step 9: 커밋**

```bash
git add app
git commit -m "feat(app): assemble the graph and wire the device list screen"
```

---

### Task 21: ADR을 accepted로 올리고 전체 검증

**Files:**
- Modify: `docs/adr/0001-kmp-single-jvm-target.md`
- Modify: `docs/adr/0002-koin-di.md`
- Modify: `docs/adr/0003-orbit-mvi.md`
- Modify: `docs/adr/0004-core-process-failures-as-values.md`
- Modify: `docs/adr/README.md`

**Interfaces:**
- Consumes: Task 1~20 전부
- Produces: 없음 (문서)

- [ ] **Step 1: 전체 빌드와 테스트**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. 모든 모듈의 테스트가 통과한다.

- [ ] **Step 2: ADR 네 건의 status 갱신**

각 파일 frontmatter의 `status: proposed`를 `status: accepted`로 바꾼다.

- [ ] **Step 3: README 인덱스 갱신**

`docs/adr/README.md` 테이블의 Status 열 네 개를 `accepted`로 바꾼다.

- [ ] **Step 4: 커밋**

```bash
git add docs/adr
git commit -m "docs: accept the scaffold ADRs now that the code exists"
```

---

## PR 분할

PR 4개로 나누고 `develop`에 순차 머지한다. 각 PR은 직전 PR이 머지된 뒤의 `develop`에서 분기한다 — 스택 PR로 쌓지 않는다.

| PR | 태스크 | 브랜치 | 머지 시점의 상태 |
|---|---|---|---|
| 1 | 1~3 | `citytexi/scaffold-build-foundation` | ADR 4건 + 빌드 기반. `./gradlew run`으로 창이 뜬다. 버전 조합 스파이크가 여기서 판가름 난다 |
| 2 | 4~10 | `citytexi/scaffold-core-process` | `core:process` 완성. 앱 동작은 그대로이고 테스트만 는다 |
| 3 | 11~17 | `citytexi/scaffold-domain-data` | `Outcome`·domain·data. 소비자가 0이라 화면 변화가 없다 |
| 4 | 18~21 | `citytexi/scaffold-devices-screen` | feature + app 조립. 화면이 실제로 디바이스를 보여준다 |

**진행 규율**

- 실행은 `superpowers:subagent-driven-development`로 한다. Orca 터미널이므로 `orca-plan-ledger`를 함께 로드해 워크스페이스 카드에 진행을 찍는다.
- 커밋은 태스크마다. push·PR 생성·머지는 **각 단계마다 사용자 확인을 받는다.**
- PR 1의 버전 스파이크(Task 3 Step 6·7)가 실패하면 PR을 올리지 않고 멈춰 보고한다. 그 결과에 따라 카탈로그와 컨벤션 플러그인이 함께 바뀌므로 뒤 PR의 전제가 무너진다.

## 자체 검토 결과

**스펙 커버리지.** 모듈 7개(Task 2·3·4·12·13·18), 컨벤션 플러그인 6개(Task 2·3·13·18), 카탈로그(Task 2), `CommandRunner` 계약 다섯 성질(Task 4~10), 실행 파일 탐색(Task 13), 수직 슬라이스(Task 14~20), ADR 4건(Task 1·21), 버전 조합 스파이크(Task 3), dmg 실행 환경 검증(Task 20 Step 8) — 스펙의 각 절이 태스크로 대응된다.

**스펙에서 의도적으로 뺀 것.** `core:common`의 디스패처 추상화와 로깅은 만들지 않는다(Task 11에 근거 기재). 지금 쓸 자리가 없다.

**버전에 민감한 지점.** `orbit-test`의 테스트 DSL(Task 18 Step 2)과 Orbit의 `syntax.simple` 패키지 경로(Task 18 Step 4)는 Orbit 메이저 버전마다 바뀐 이력이 있다. 컴파일되지 않으면 12.0.0 아티팩트의 KDoc을 확인해 같은 의미의 호출로 바꾼다.

**중단 조건.** Task 3 Step 6에서 `NoSuchMethodError`·`AbstractMethodError`가 나면 Kotlin 2.3.21 + Compose 1.11.1 조합이 실패한 것이다. 그 경우 진행하지 말고 사용자에게 보고한다 — 버전이 바뀌면 카탈로그와 컨벤션 플러그인이 함께 움직인다.

