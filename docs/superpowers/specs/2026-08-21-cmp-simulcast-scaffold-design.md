# cmp-simulcast 초기 스캐폴드 설계

> 작성 2026-08-21 · 대상 저장소 `citytexi/cmp-simulcast` · 상태: 승인됨(구현 계획 대기)

## 배경

`cmp-simulcast`는 macOS 데스크톱 개발자 도구다. Android AVD와 iOS 시뮬레이터를 나란히 두고
각각의 로그를 한 화면에서 보며, 두 플랫폼에 같은 조작(설치·실행·딥링크·권한 초기화·스크린샷)을
한 번에 내리는 것이 목적이다. 최종적으로는 그 조작 표면을 MCP로 노출해 에이전트가
"두 플랫폼에 설치하고 딥링크 열고 로그를 비교해줘"를 수행할 수 있게 한다.

제품 로드맵은 네 단계로 잡혀 있다.

| 단계 | 내용 |
|---|---|
| v0.1 | 창 배치 제어 + 양쪽 로그 스트림 + 필터·검색 |
| v0.2 | 커맨드 팔레트 — 설치·실행·딥링크·권한 초기화·스크린샷을 양쪽에 동시 실행 |
| v0.3 | MCP 서버로 조작 표면 노출 |
| v1.0 | 시뮬레이터 화면 임베딩 (Android 먼저, iOS는 그다음) |

화면 임베딩(v1.0)이 기술적으로 가장 어렵다. macOS에는 다른 프로세스의 `NSWindow`를 내 앱 창
안으로 reparent 하는 공개 API가 없어, 픽셀 스트리밍(Android는 emulator gRPC 또는 scrcpy 프로토콜,
iOS는 ScreenCaptureKit 창 캡처)으로 우회해야 하며 전체 공수의 큰 비중을 차지한다. 이 설계는
그 단계를 다루지 않는다. 앱의 핵심 가치가 "화면을 한 창에 모으는 것"이 아니라 "양쪽을 한 번에
조작하고 로그를 나란히 비교하는 것"에 있으므로, 임베딩 없이도 제품이 성립한다는 판단이다.

## 이 문서의 범위

**포함:** 모듈 구조와 의존 규칙, `build-logic` 컨벤션 플러그인 세트, 버전 카탈로그,
`core/process`의 외부 프로세스 실행 계약, 그리고 전 레이어를 관통하는 수직 슬라이스 하나
(연결된 디바이스 목록 조회·표시).

**제외:** 로그 스트리밍 UI, 창 배치 제어, 커맨드 팔레트, MCP 서버, 화면 임베딩, 코드 서명과
공증. 이들은 후속 스펙에서 각각 다룬다.

수직 슬라이스를 하나 포함하는 이유는 검증이다. 빈 모듈만 세우면 의존 방향·컨벤션 플러그인·DI
조립·테스트 배선이 실제로 맞는지 다음 라운드에 가서야 드러난다. 디바이스 목록 조회는 외부
프로세스를 실제로 실행하고 두 플랫폼 결과를 합치므로, 이 앱이 앞으로 할 일의 축소판이면서
가장 작다.

## 기술 선택

| 항목 | 선택 | 근거 |
|---|---|---|
| 빌드 타깃 | Kotlin Multiplatform, `jvm()` 타깃 하나 | 지금은 Desktop 하나뿐이라 KMP 레이어가 순수 비용이다. 그럼에도 KMP로 두는 것은 나중에 Windows·Linux에서 Android 쪽만 지원하거나 코어 로직을 다른 타깃에 올릴 때 플러그인·소스셋 구조를 갈아엎지 않고 타깃 한 줄만 추가하면 되기 때문이다. 지불하는 비용은 `commonMain`/`jvmMain` 계층과 expect/actual 장단이다 |
| UI | Compose Multiplatform 1.11.1 | 최신 안정판. 1.12.0은 rc 단계라 채택하지 않는다 |
| Kotlin | 2.4.10 | 최신 안정판. CMP 1.11은 Kotlin 언어·API 버전 2.2 이상을 요구하며, 릴리스 노트가 말하는 "Kotlin 2.3 필요"는 native·web 타깃 한정이라 JVM 단일 타깃인 이 프로젝트에는 걸리지 않는다. 아래 [리스크](#리스크와-검증) 참고 |
| DI | Koin 4.2.2 | 모듈마다 `module { }` 정의를 두고 `app`이 모아 시작하는 구조라, feature가 늘어도 `app`의 변경이 한 줄이다. KSP가 들어오지 않아 컨벤션 플러그인과 빌드 시간이 단순해진다. 누락을 런타임에 알게 되는 것이 대가이므로 `verify()` 테스트로 상쇄한다 |
| 상태관리 | Orbit MVI 12.0.0 | `orbit-viewmodel-desktop`·`orbit-compose-desktop` 아티팩트가 있어 KMP를 정식 지원한다 |
| 테스트 | `kotlin.test` + `orbit-test` + Turbine | `kotlin.test`는 멀티플랫폼 대응이라 나중에 타깃이 늘어도 그대로 간다 |

## 모듈 구조

```
cmp-simulcast/
├── build-logic/              (composite build)
├── gradle/libs.versions.toml
├── app/
├── core/
│   ├── common/
│   ├── designsystem/
│   └── process/
├── domain/
├── data/
└── feature/
    └── devices/
```

| 모듈 | 책임 | 의존 |
|---|---|---|
| `app` | 진입점, 창 생성, Koin 모듈 조립, 인터페이스↔구현 바인딩 | 전부 |
| `core:common` | 코루틴 디스패처 추상화, `Result` 확장, 로깅 | 없음 |
| `core:designsystem` | 테마·토큰·공용 컴포저블 | `core:common` |
| `core:process` | 외부 프로세스 실행과 출력 스트리밍 | `core:common` |
| `domain` | 모델, Repository 인터페이스, UseCase | `core:common` |
| `data` | adb·simctl 어댑터, Repository 구현 | `domain`, `core:process`, `core:common` |
| `feature:devices` | 디바이스 목록 화면과 상태 홀더 | `domain`, `core:designsystem`, `core:common` |

### 의존 규칙

의존은 한 방향으로만 흐르고 역방향이 없다.

- **`feature`는 `data`를 모른다.** `domain`의 인터페이스만 본다. 구현 바인딩은 `app`의 Koin
  조립에서 한 번 일어난다. 이 규칙을 문서로만 두지 않고 `simulcast.feature` 컨벤션 플러그인이
  `domain`·`core:designsystem`·`core:common`만 자동 의존시켜 물리적으로 강제한다. feature가
  `data`를 참조하려면 build 파일에 직접 적어야 하고, 그것이 리뷰에서 눈에 띈다.
- **`core:process`는 앱 지식이 0이다.** adb도 simctl도 모른다. 다른 프로젝트에 그대로 떼어 갈 수
  있는 상태를 유지한다.
- **`core:common`은 아무것도 참조하지 않는다.**

### `core:process`를 따로 두는 이유

adb든 simctl든 결국 "프로세스를 띄우고 줄 단위로 읽는다"로 수렴한다. 그 한 덩어리가 앱 전체에서
가장 많이 재사용되고, 동시에 제대로 테스트하기 가장 어려운 부분이다 — 프로세스 수명, 타임아웃,
취소 시 자식 프로세스 회수가 모두 여기 모인다. `data`에 묻으면 `data`가 커질수록 이 어려움이
어댑터 로직과 섞여 테스트가 통째로 무거워진다.

## `core:process` 계약

```kotlin
data class Command(
    val executable: Path,
    val args: List<String>,
)

sealed interface CommandResult {
    data class Success(val stdout: String) : CommandResult
    data class Failure(val exitCode: Int, val stderr: String) : CommandResult
}

interface CommandRunner {
    /** 단발성 실행. `adb devices` 처럼 끝나는 명령. */
    suspend fun run(command: Command, timeout: Duration): CommandResult

    /** 장수명 스트림. `logcat` 처럼 끝나지 않는 명령. */
    fun stream(command: Command): Flow<String>
}
```

두 가지가 계약의 핵심이다.

**프로세스 수명이 Flow 수명에 묶인다.** `stream`은 `callbackFlow` + `awaitClose`로 구현하고,
수집이 취소되면 `destroy()` 후 유예 시간 내에 끝나지 않으면 `destroyForcibly()`까지 간다. 이걸
하지 않으면 앱을 닫은 뒤에도 logcat 프로세스가 살아남는다. 로그 스트림을 켜고 끄는 것이 이 앱의
상시 동작이라 누수가 곧바로 쌓인다.

**셸을 거치지 않는다.** `Command`는 실행 파일 경로와 인자 리스트를 분리해 들고,
`ProcessBuilder(executable, *args)`로 직접 실행한다. 문자열 하나로 합쳐 `sh -c`에 넘기면 디바이스
이름이나 경로에 공백·따옴표가 들어갈 때 깨지고, 인자에 사용자 입력이 섞이는 순간 주입 표면이
생긴다.

읽기는 `Dispatchers.IO`에서 `BufferedReader.lineSequence()`로 한다. 실행 파일 탐색
(`ANDROID_HOME`, `xcrun -f simctl`)은 앱 지식이므로 `data`의 몫이다.

## 수직 슬라이스: 디바이스 목록

`DeviceRepository`가 두 소스를 병렬로 조회해 하나의 목록으로 합친다.

- Android: `adb devices -l`
- iOS: `xcrun simctl list devices --json`

```kotlin
enum class DevicePlatform { ANDROID, IOS }

/** 두 플랫폼의 상태 어휘를 공통 축으로 접는다. adb의 `device`/`offline`/`unauthorized`와
 *  simctl의 `Booted`/`Shutdown`이 각각 여기로 매핑된다. */
enum class DeviceState { RUNNING, STOPPED, UNAVAILABLE }

data class Device(
    val id: String,
    val name: String,
    val platform: DevicePlatform,
    val state: DeviceState,
)
```

화면은 Orbit `ContainerHost`로 `DeviceListState`를 들고 두 플랫폼을 좌우 2열로 보여준다.
조작은 새로고침 하나뿐이다.

**한쪽 실패가 다른 쪽을 죽이지 않는다.** Xcode가 없는 기계에서 `xcrun`을 찾지 못했다고 Android
목록까지 못 보면 안 된다. 각 소스를 독립적으로 `Result`로 받아 부분 성공을 상태에 싣고, 실패한
쪽은 그 열에만 사유를 표시한다.

## 에러 처리

`domain`에 세 갈래를 둔다.

```kotlin
sealed interface DeviceError {
    data class ToolNotFound(val tool: String) : DeviceError
    data class ToolFailed(val tool: String, val exitCode: Int, val stderr: String) : DeviceError
    data class Timeout(val tool: String) : DeviceError
}
```

`CommandResult`에서 `DeviceError`로의 변환은 `data` 경계에서 한 번 일어난다. `domain`과
`feature`는 프로세스라는 개념을 모른다. `CancellationException`은 잡지 않고 재던진다.

세 갈래로 나눈 것은 사용자에게 줄 안내가 각각 다르기 때문이다. `ToolNotFound`는 설치나 PATH
문제이고, `ToolFailed`는 도구가 뱉은 메시지를 그대로 보여줘야 하며, `Timeout`은 재시도가 답이다.

## build-logic

루트 `settings.gradle.kts`에서 `includeBuild("build-logic")`으로 composite build로 참여시킨다.
버전은 `gradle/libs.versions.toml` 하나에 모으고, 컨벤션 플러그인이 그 카탈로그를 읽는다.

| 플러그인 ID | 적용 대상 | 하는 일 |
|---|---|---|
| `simulcast.kmp.library` | 모든 모듈 | `kotlin("multiplatform")` + `jvm()` 타깃 + JVM toolchain + `kotlin.test` 배선 |
| `simulcast.compose` | UI가 있는 모듈 | Compose Multiplatform 플러그인 + Compose 컴파일러 플러그인 |
| `simulcast.koin` | DI 정의가 있는 모듈 | Koin 의존성 + `koin-test` |
| `simulcast.feature` | `feature/*` | 위 셋을 묶고 `domain`·`core:designsystem`·`core:common`·Orbit 의존을 자동 주입 |
| `simulcast.desktop.app` | `app` | `compose.desktop.application` + `nativeDistributions`(dmg) |

`simulcast.feature`가 의존을 자동 주입하므로 feature 모듈의 build 파일은 `plugins { }` 한 블록으로
끝난다. feature가 늘어날 때 복사해야 할 보일러플레이트가 없고, 앞서 말한 "feature는 data를
모른다" 규칙이 기본값으로 지켜진다.

패키징은 `nativeDistributions`에 dmg 타깃과 앱 이름·번들 ID까지만 설정한다. 코드 서명과 공증은
화면 기록·손쉬운 사용 권한이 필요해지는 v0.1 이후에 다룬다. 그 권한은 `.app` 번들 단위로
부여되므로, 서명·공증이 없으면 권한 다이얼로그가 앱 이름이 아니라 "Java"로 뜬다.

## 테스트 전략

- **`core:process`** — `CommandRunner` 구현은 `echo`·`sleep` 같은 실존 명령으로 통합 테스트한다.
  검증 대상이 타임아웃 동작, 취소 시 자식 프로세스 회수, 스트림 종료라서 fake로는 아무것도
  증명되지 않는다.
- **`data`** — `FakeCommandRunner`로 고정 출력을 주고 파싱과 에러 변환을 검증한다. 실제 `adb`
  출력 샘플을 픽스처로 둔다.
- **`feature`** — `orbit-test`(`org.orbit-mvi:orbit-test`)로 상태 전이를 검증한다. 한쪽 소스 실패
  시 다른 쪽이 살아 있는지가 핵심 케이스다.
- **DI** — 모듈마다 Koin `verify()` 테스트를 건다. 런타임 누락을 빌드 시점으로 당기는 장치라
  Koin을 고른 대가를 여기서 갚는다.

## 리스크와 검증

**Compose MP 1.11.1 + Kotlin 2.4.10 호환.** Compose 컴파일러 플러그인이 Kotlin 배포에 포함되면서
두 버전의 결합이 이전보다 강해졌다. CMP 1.11이 요구하는 하한(언어·API 2.2)은 충족하지만 상한은
릴리스 노트에 없다. 구현 계획의 첫 태스크로 빈 모듈 하나를 실제로 컴파일해 검증하고, 맞지 않으면
Kotlin을 2.3 계열로 내린다. 이 검증을 먼저 두는 이유는 여기서 버전이 바뀌면 카탈로그와 컨벤션
플러그인이 함께 움직이기 때문이다.

**버전은 저장소 메타데이터로 확인한다.** 이 스펙의 버전은 `repo1.maven.org`의
`maven-metadata.xml`을 직접 읽어 확정했다. `search.maven.org`의 검색 색인은 최신 릴리스를
반영하지 않는 경우가 있어 근거로 쓰지 않는다.

**`xcrun simctl list --json` 출력 스키마.** Xcode 버전에 따라 필드가 달라질 수 있다. 파싱은
관대하게 — 모르는 필드는 무시하고, 필수 필드가 없으면 그 항목만 건너뛰고 나머지는 살린다.

**Koin 런타임 누락.** `verify()` 테스트로 막되, 그 테스트를 잊고 모듈을 추가하는 경우가 남는다.
feature 모듈이 세 개를 넘어가면 CI에서 전체 모듈 `verify()`를 한 번에 도는 태스크를 검토한다.

## 대응 ADR

이 스펙이 확정하는 결정 중 다음은 구현과 함께 `docs/adr/`에 기록한다. 코드만 보고는 대안과
기각 사유를 알 수 없는 것들이다.

- KMP multiplatform에 `jvm()` 타깃 하나 (JVM 단일 타깃 기각)
- DI로 Koin 채택 (kotlin-inject·수동 조립 기각)
- 상태관리로 Orbit MVI 채택
- `core:process`를 `data`에서 분리

## 다음 단계

이 스펙이 승인되면 `superpowers:writing-plans`로 구현 계획을 만든다. 계획의 Global Constraints에
CLAUDE.md의 주석 규약 요지를 싣는다 — 코드가 이미 말하는 것은 쓰지 않기, `@return`·`@param`은
타입·이름이 말하지 못할 때만, 다른 컴포넌트의 현재 상태를 단정하지 않기.
