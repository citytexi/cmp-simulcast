---
id: module-structure
title: 모듈 구조와 의존성 규칙
category: architecture
status: living
platforms: desktop
verified: 2026-08-31
related_adr: [ADR-0001, ADR-0002, ADR-0003, ADR-0004]
related_architecture:
related_code:
  - Outcome
  - CommandRunner
  - Command
  - CommandResult
  - ProcessCommandRunner
  - DeviceRepository
  - GetDevicesUseCase
  - DeviceListing
  - DeviceError
  - ToolLocator
  - AndroidDeviceSource
  - IosDeviceSource
  - parseSimctlDevices
  - DeviceRepositoryImpl
  - dataModule
  - DeviceListState
  - DeviceListViewModel
  - devicesModule
  - DeviceListScreen
  - AppModules
  - platformModule
  - Main
  - KoinGraphTest
  - AppTheme
tags: [architecture, gradle, di]
---

# 모듈 구조와 의존성 규칙

> 상시 갱신되는 **구현 가이드**("어떻게/어디"). 결정 근거(why)는 [`../adr/`](../adr/README.md).
> 근거는 파일명 + 심볼명으로만. 라인번호·모듈 개수 등 변동 수치는 적지 않는다(→ [`../adr/README.md`](../adr/README.md)).

## 모듈 그래프

```
app
 ├─ core:designsystem
 ├─ core:process
 ├─ data ────────┐
 ├─ domain        │
 └─ feature:devices
                  │
feature:devices   │
 ├─ domain ◄──────┘
 └─ core:designsystem

data
 ├─ domain
 └─ core:process

domain
 └─ core:common

core:process
 └─ core:common

core:designsystem
 └─ core:common

core:common
 └─ (없음)
```

의존성은 위 다이어그램의 화살표 방향으로만 흐르고 역방향 참조는 없다 — 하위 레이어(`core:common`)가 상위 레이어를 참조하는 선은 어디에도 없다. `feature:devices`에서 `data`로 가는 선은 없다 — 아래 [규칙](#어겨서는-안-되는-규칙과-강제-지점) 참고. `core:designsystem`을 참조하는 모듈은 `app`과 `feature:devices`다.

## 컨벤션 플러그인과 각 모듈이 얹는 것

의존성 규칙은 문서가 아니라 `build-logic/src/main/kotlin/*.gradle.kts`가 물리적으로 강제한다. 어떤 플러그인이 무엇을 주입하는지가 이 저장소에서 규칙이 실제로 지켜지는 방식이다.

| 컨벤션 플러그인 | 적용 모듈 | 주입하는 것 |
|---|---|---|
| `simulcast.kmp` | 모든 모듈의 바탕 | `kotlin("multiplatform")` + `jvm()` 단일 타깃(`jvmToolchain(21)`), `commonMain`에 `kotlinx-coroutines-core`, `commonTest`에 `kotlin("test")` + `kotlinx-coroutines-test`. 프로젝트 의존성은 주입하지 않는다. |
| `simulcast.compose` | `core:designsystem`, `simulcast.feature`·`simulcast.desktop.app`을 통해 `feature:devices`·`app` | `simulcast.kmp` + `org.jetbrains.compose` + `org.jetbrains.kotlin.plugin.compose`. |
| `simulcast.koin` | `data`, `simulcast.feature`를 통해 `feature:devices` | `simulcast.kmp` + `commonMain`에 `koin-core`, `commonTest`에 `koin-test`. |
| `simulcast.serialization` | `data` | `simulcast.kmp` + `org.jetbrains.kotlin.plugin.serialization` + `commonMain`에 `kotlinx-serialization-json`. |
| `simulcast.feature` | `feature:devices` | `simulcast.compose` + `simulcast.koin` 위에 `commonMain`에서 `api(project(":domain"))`, `implementation(project(":core:designsystem"))`, `api(orbit-core)`, `implementation(orbit-viewmodel, orbit-compose, koin-compose, koin-composeViewmodel)`; `commonTest`에 `orbit-test`, `turbine`. **`project(":data")`는 여기 없다** — `feature` 계열 모듈은 이 플러그인을 통해서만 프로젝트 의존성을 받으므로, `data`가 안 보이는 게 곧 "feature는 data를 모른다" 규칙의 강제 지점이다. |
| `simulcast.desktop.app` | `app` | `simulcast.compose` 위에 데스크톱 실행/패키징(`compose.desktop.application`의 `mainClass`, `nativeDistributions`, `TargetFormat.Dmg`, `packageName`, `bundleID`)과 Gradle 데몬 JVM과 무관하게 JDK 21로 고정하는 `javaHome` 툴체인 설정. 프로젝트 의존성은 주입하지 않는다. |

각 모듈의 `build.gradle.kts`는 컨벤션 플러그인이 주지 않는 것만 덧붙인다:

- `core:common/build.gradle.kts` — `simulcast.kmp`만 적용하고 그 외 아무것도 없다. 프로젝트 의존성 없음이 곧 "core:common은 아무것도 의존하지 않는다" 규칙 자체다.
- `core:process/build.gradle.kts` — `simulcast.kmp` + `implementation(project(":core:common"))`. `adb`·`emulator`·`simctl` 같은 문자열은 이 모듈 어디에도 없다(아래 참고).
- `core:designsystem/build.gradle.kts` — `simulcast.compose` + `implementation(compose.material3)` + `implementation(project(":core:common"))`.
- `domain/build.gradle.kts` — `simulcast.kmp` + `api(project(":core:common"))`. `Outcome`이 `DeviceListing`·`DeviceRepository`의 공개 시그니처에 그대로 노출되므로 `api`.
- `data/build.gradle.kts` — `simulcast.kmp` + `simulcast.serialization` + `simulcast.koin` 위에 `implementation(project(":domain"))`, `implementation(project(":core:process"))`.
- `feature/devices/build.gradle.kts` — `simulcast.feature` 위에 `implementation(compose.material3)` 하나만 추가.
- `app/build.gradle.kts` — `simulcast.desktop.app` 위에 `jvmMain`에서 `compose.desktop.currentOs`, `compose.material3`, `kotlinx-coroutines-swing`, 그리고 `core:designsystem`·`core:process`·`data`·`domain`·`feature:devices` 전체와 `koin-core`·`koin-compose`를 `implementation`으로 묶는다. **이 프로젝트들을 전부 아는 것은 `app`뿐이다** — 합성 루트이기 때문.

## 어겨서는 안 되는 규칙과 강제 지점

- **`feature`는 `data`를 보지 않는다.** `simulcast.feature`가 주입하는 프로젝트 의존성은 `domain`과 `core:designsystem`뿐이고, `feature/devices/build.gradle.kts`도 `data`를 추가하지 않는다. `feature:devices`의 코드(`DeviceListViewModel`)는 `GetDevicesUseCase`(도메인)만 참조하고 `DeviceRepositoryImpl`·`AndroidDeviceSource`·`IosDeviceSource`(데이터) 심볼은 import하지 않는다.
- **`core:process`는 adb/simctl/emulator를 모른다.** `Command`·`CommandRunner`·`CommandResult`·`ProcessCommandRunner`는 실행 파일 경로와 인자 배열, 타임아웃만 다루는 범용 프로세스 실행기다. 어떤 도구를 어디서 찾고 어떤 인자를 넘기는지는 `data`의 `ToolLocator`(`adb()`·`emulator()`·`xcrun()`)와 `AndroidDeviceSource`·`IosDeviceSource`가 안다.
- **`core:common`은 아무것도 의존하지 않는다.** `core/common/build.gradle.kts`가 `simulcast.kmp` 외에 아무 프로젝트 의존성도 얹지 않는 것으로 확인된다.
- **실패는 `Outcome` 값이지 `kotlin.Result`도, `throw`도 아니다.** `domain`의 `DeviceError`(`ToolNotFound`·`ToolFailed`·`Timeout`·`ParseFailed`)와 `core:process`의 `CommandResult`(`Completed`·`TimedOut`·`StartFailed`)가 실패를 값으로 표현하는 두 축이다. `AndroidDeviceSource.list()`·`IosDeviceSource.list()`는 실패마다 `Outcome.Err`를 반환하지 `throw`하지 않는다. 이 결정의 근거는 [ADR-0004](../adr/0004-core-process-failures-as-values.md).
- **`CancellationException`은 캐치되지 않는다.** `core:process`·`data` 어디에도 `catch (e: Exception)`·`catch (e: Throwable)` 같은 넓은 캐치가 없다. `ProcessCommandRunner`는 `IOException`만, `IosDeviceSource`·`SimctlJson`은 `SerializationException`·`IllegalStateException`만 잡는다. JVM에서 `CancellationException`이 `IllegalStateException`의 서브클래스이므로, `IosDeviceSource.list()`는 이를 명시적으로 잡아 다시 던진다(rethrow) — 이 가드 없이는 아래 `catch (e: IllegalStateException)`이 취소를 삼킨다. 취소를 삼키지 않아야 한다는 근거는 [ADR-0004](../adr/0004-core-process-failures-as-values.md).

## 새 모듈을 어디에 둘지

- **공유 타입/유틸(다른 모든 레이어가 참조 가능)**: `core:common`에 두고 `simulcast.kmp`만 적용한다. 이 모듈은 프로젝트 의존성을 갖지 않는다는 규칙을 지켜야 한다.
- **외부 프로세스/시스템 콜을 감싸는 새 실행기**: `core:process`를 확장하거나 그 옆에 새 `core:*` 모듈을 만들고 `simulcast.kmp` + `implementation(project(":core:common"))`을 적용한다. 특정 CLI 도구 이름(adb 등)이 여기 들어오면 규칙 위반이다.
- **새 유스케이스/리포지토리 인터페이스**: `domain`에 추가한다. 이미 `simulcast.kmp` + `api(project(":core:common"))`가 적용돼 있다.
- **새 데이터 소스/리포지토리 구현체**: `data`에 추가한다. `simulcast.kmp` + `simulcast.serialization`(JSON 파싱이 필요하면) + `simulcast.koin`이 이미 적용돼 있고, `domain`·`core:process`를 `implementation`으로 참조할 수 있다.
- **새 화면**: `feature:<이름>` 모듈을 만들고 `simulcast.feature`를 적용한다. 이 플러그인이 `domain`(api)과 `core:designsystem`(implementation)을 이미 주고, `orbit-core`를 `OrbitContainerHost`의 상위 타입으로 쓸 수 있게 `api`로 노출한다. **`data`를 직접 추가하면 안 된다** — 필요한 것은 `domain`의 유스케이스뿐이어야 한다.
- **합성 루트 변경(새 모듈을 그래프에 연결)**: `app`에서만 한다. `app/build.gradle.kts`에 프로젝트 의존성을 추가하고, `AppModules.kt`의 `appModules` 리스트에 해당 Koin 모듈을 등록한다.

## `api` vs `implementation`

컨벤션 플러그인과 모듈 `build.gradle.kts`는 한 가지 기준을 따른다: **의존성의 타입이 그 모듈의 공개 API 시그니처에 그대로 나타나면 `api`, 아니면 `implementation`.**

- `domain/build.gradle.kts`의 `api(project(":core:common"))` — `Outcome`이 `DeviceRepository.listDevices()`·`GetDevicesUseCase.invoke()`의 반환 타입에 그대로 나온다.
- `simulcast.feature`의 `api(project(":domain"))` — `DeviceListViewModel`의 생성자 파라미터가 `GetDevicesUseCase`다.
- `simulcast.feature`의 `api(libs.lib("orbit-core"))` — `DeviceListViewModel`이 `OrbitContainerHost<DeviceListState, DeviceListState, Nothing>`를 상속하므로 `orbit-core`의 타입이 곧 이 클래스의 상위 타입이다.
- 그 외(`core:designsystem`, `orbit-viewmodel`, `orbit-compose`, `koin-compose`, `koin-composeViewmodel`, `core:process` 등)는 전부 `implementation` — 소비자 모듈의 공개 시그니처에 등장하지 않는다.

## 합성 루트 (`app`)

`AppModules.kt`의 `appModules`는 `platformModule`, `dataModule`, `devicesModule` 세 Koin 모듈을 순서대로 합친다.

- **`dataModule`**(`data`의 `DataModule.kt`)은 `ToolLocator`·`AndroidDeviceSource`·`IosDeviceSource`·`DeviceRepository`(→`DeviceRepositoryImpl`)를 바인딩한다. **`CommandRunner`도 `GetDevicesUseCase`도 여기서 바인딩하지 않는다** — `AndroidDeviceSource`/`IosDeviceSource` 생성자의 `get()`으로 `CommandRunner`를 요구만 할 뿐, 그 구현체를 고르는 것은 `data`의 책임이 아니다. `data`는 여러 `CommandRunner` 구현(실제 프로세스 실행기, 테스트용 페이크)에 무관해야 하므로 바인딩을 유보한다.
- **`platformModule`**(`app`의 `AppModules.kt`)이 `single<CommandRunner> { ProcessCommandRunner() }`로 실제 구현을 고르고, `factory { GetDevicesUseCase(get()) }`로 유스케이스를 조립한다. `CommandRunner`의 구체 구현 선택과 `GetDevicesUseCase`의 조립 둘 다 `domain`·`data` 경계를 넘나드는 합성이라 `app`에 있다.
- **`devicesModule`**(`feature:devices`의 `DevicesModule.kt`)은 `viewModelOf(::DeviceListViewModel)`로 뷰모델만 바인딩한다.

`Main.kt`의 `main()`은 `application { Window(...) { KoinApplication(configuration = koinConfiguration { modules(appModules) }) { AppTheme { DeviceListScreen() } } } }`로 이 세 모듈을 묶은 `appModules`를 Compose 트리 루트에 걸고, 그 안에서 `DeviceListScreen()`을 그린다 — Koin 그래프 구성과 화면 진입이 한 지점에서 이뤄진다.

`app/src/jvmTest/kotlin/.../KoinGraphTest.kt`의 `KoinGraphTest`는 `appModules`로 그래프를 구성하고 `koin.get<DeviceListViewModel>()`(최상위 진입점)이 resolve되는지만 확인한다. 이 테스트가 검증하는 것은 "진입점에서 도달 가능한 그래프가 시작 시점에 조립 가능한가"이지, 모든 정의가 다 쓰이는가가 아니다 — 진입점에서 닿지 않는 바인딩이 있어도 이 테스트는 통과한다.

## 기기 목록 데이터 흐름

1. `DeviceListScreen`(`feature:devices`)이 `koinViewModel()`로 얻은 `DeviceListViewModel`의 `collectAsState()`로 `DeviceListState`를 구독하고, `LaunchedEffect(Unit)`에서 `refresh()`를 호출한다.
2. `DeviceListViewModel.refresh()`는 Orbit `intent { }` 안에서 `loading = true`로 리듀스한 뒤 생성자로 주입된 `GetDevicesUseCase`를 호출하고, 결과로 받은 `DeviceListing`의 `android`·`ios` `Outcome`을 그대로 state에 반영한다.
3. `GetDevicesUseCase.invoke()`는 `DeviceRepository.listDevices()`를 그대로 위임한다.
4. `DeviceRepositoryImpl.listDevices()`(`data`)가 `coroutineScope` 안에서 `async { android.list() }`와 `async { ios.list() }`를 **동시에** 실행하고 두 결과를 기다려 `DeviceListing(androidResult.await(), iosResult.await())`을 만든다.
5. `DeviceListing`은 `android`·`ios` 각각 독립된 `Outcome<List<Device>, DeviceError>`를 갖는다 — 한쪽이 `Outcome.Err`가 돼도 다른 쪽의 `Outcome.Ok`는 영향받지 않는다. 이 부분 성공(partial-success) 속성이 `android`/`ios`를 하나로 합치지 않고 따로 든 이유다.
6. `DeviceListScreen`의 `DeviceColumn`은 각 `Outcome`을 `null`(조회 전) / `Err`(에러 문구) / `Ok`(빈 목록 또는 `LazyColumn`)로 각각 독립 렌더링하므로, 안드로이드 쪽 `adb`가 없어도 iOS 쪽 시뮬레이터 목록은 그대로 보인다.
