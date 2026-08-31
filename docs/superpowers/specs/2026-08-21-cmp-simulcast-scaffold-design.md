# cmp-simulcast 초기 스캐폴드 설계

> 작성 2026-08-21 · 개정 2026-08-21(스펙 리뷰 반영) · 대상 저장소 `citytexi/cmp-simulcast`
> 상태: 승인됨(구현 계획 대기)

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
`core:process`의 외부 프로세스 실행 계약, 그리고 전 레이어를 관통하는 수직 슬라이스 하나
(연결·정지된 디바이스 목록 조회·표시).

**제외:** 로그 스트리밍 UI, 창 배치 제어, 커맨드 팔레트, MCP 서버, 화면 임베딩, 코드 서명과
공증. 이들은 후속 스펙에서 각각 다룬다.

수직 슬라이스를 하나 포함하는 이유는 검증이다. 빈 모듈만 세우면 의존 방향·컨벤션 플러그인·DI
조립·테스트 배선이 실제로 맞는지 다음 라운드에 가서야 드러난다. 디바이스 목록 조회는 외부
프로세스를 실제로 실행하고 두 플랫폼 결과를 합치므로, 이 앱이 앞으로 할 일의 축소판이면서
가장 작다.

## 기술 선택

| 항목 | 선택 | 근거 |
|---|---|---|
| 빌드 타깃 | Kotlin Multiplatform, `jvm()` 타깃 하나 | 지금은 Desktop 하나뿐이라 KMP 레이어가 순수 비용이다. 그럼에도 KMP로 두는 것은 나중에 Windows·Linux 데스크톱을 지원할 때 플러그인·소스셋 구조를 갈아엎지 않고 타깃 한 줄만 추가하면 되기 때문이다. 지불하는 비용은 `commonMain`/`jvmMain` 계층이다 |
| Kotlin | 2.3.21 | 아래 [버전 조합](#버전-조합) 참고 |
| Compose Multiplatform | 1.11.1 | 위와 같음 |
| material3 | 1.9.0 | 우리 카탈로그가 아니라 Compose Multiplatform 1.11.1 Gradle 플러그인이 고르는 버전이다(`ComposeBuildConfig.composeMaterial3Version`). 그래서 `checkJvmMainComposeLibrariesCompatibility` 경고가 빌드에 찍힌다 — material3 API 차이를 디버깅할 때는 1.11.1이 아니라 이 버전의 문서를 봐야 한다 |
| JVM toolchain | 21 | `jpackage`가 JDK 17 이상을 요구하고, Compose Hot Reload는 JetBrains Runtime 호환을 위해 타깃 21 이하를 요구한다. 둘을 동시에 만족하는 값이다 |
| Gradle | 9.7.1 | wrapper로 고정. KGP 2.3.21과의 조합은 스파이크에서 확인한다 |
| DI | Koin 4.2.2 | 모듈마다 `module { }` 정의를 두고 `app`이 모아 시작하는 구조라, feature가 늘어도 `app`의 변경이 한 줄이다. KSP가 들어오지 않아 컨벤션 플러그인과 빌드 시간이 단순해진다 |
| 상태관리 | Orbit MVI 12.0.0 | `orbit-viewmodel-desktop`·`orbit-compose-desktop` 아티팩트가 있어 KMP를 정식 지원한다 |
| ViewModel 배선 | `lifecycle-viewmodel-compose` + `koin-compose-viewmodel` 4.2.2 | Orbit의 `ContainerHost`는 androidx `ViewModel` 기반이라, 컴포저블에서 인스턴스를 얻으려면 이 둘이 필요하다. lifecycle 버전은 CMP 1.11.1이 물고 오는 것을 그대로 쓴다 |
| JSON | kotlinx-serialization 1.11.0 | `simctl list --json` 파싱용. 컴파일러 플러그인이라 컨벤션 플러그인과 함께 움직인다 |
| 코루틴 | kotlinx-coroutines 1.11.0 | |
| 테스트 | `kotlin.test` + `orbit-test` + Turbine 1.2.1 | `kotlin.test`는 멀티플랫폼 대응이라 나중에 타깃이 늘어도 그대로 간다 |

### 버전 조합

Kotlin 2.4.10 + CMP 1.11.1을 쓰지 않는다. CMP 1.11.1은 Kotlin 2.4.0보다 이틀 먼저 나왔고 CMP
CHANGELOG 전문에 "2.4"가 한 번도 등장하지 않는다 — CMP 1.11 라인 전체가 Kotlin 2.4 이전에
만들어졌다. JetBrains의 "최신 CMP는 항상 최신 Kotlin과 호환된다"는 안내가 보증하는 범위 밖이고,
깨진다면 Compose 컴파일러 ABI나 번들된 Compose Hot Reload의 바이트코드 계측에서 런타임
`NoSuchMethodError`로 드러나기 쉬워 발견이 늦다.

Kotlin 2.3.21 + CMP 1.11.1은 같은 시기에 나온 안정판 조합이다. Kotlin 2.4로 올리는 것은 CMP
1.12가 정식 출시된 뒤에 한 번에 한다.

**버전은 저장소 메타데이터로 확인한다.** 이 스펙의 버전은 `repo1.maven.org`의
`maven-metadata.xml`을 직접 읽어 확정했다. `search.maven.org`의 검색 색인은 최신 릴리스를
반영하지 않는 경우가 있어 근거로 쓰지 않는다.

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
| `core:common` | 코루틴 디스패처 추상화, `Outcome` 타입, 로깅 | 없음 |
| `core:designsystem` | 테마·토큰·공용 컴포저블 | `core:common` |
| `core:process` | 외부 프로세스 실행과 출력 스트리밍 | `core:common` |
| `domain` | 모델, Repository 인터페이스, UseCase | `core:common` |
| `data` | adb·simctl·emulator 어댑터, Repository 구현 | `domain`, `core:process`, `core:common` |
| `feature:devices` | 디바이스 목록 화면과 상태 홀더 | `domain`, `core:designsystem`, `core:common` |

### 의존 규칙

의존은 한 방향으로만 흐르고 역방향이 없다.

- **`feature`는 `data`를 모른다.** `domain`의 인터페이스만 본다. 구현 바인딩은 `app`의 Koin
  조립에서 한 번 일어난다. 이 규칙을 문서로만 두지 않고 `simulcast.feature` 컨벤션 플러그인이
  `domain`·`core:designsystem`·`core:common`만 자동 의존시켜 물리적으로 강제한다. feature가
  `data`를 참조하려면 build 파일에 직접 적어야 하고, 그것이 리뷰에서 눈에 띈다.
- **`core:process`는 앱 지식이 0이다.** adb도 simctl도 모르고, `core:common`의 디스패처 추상화
  외에는 아무것도 참조하지 않는다. 그 의존 하나만 끊으면 다른 프로젝트로 떼어 갈 수 있는
  상태를 유지한다.
- **`core:common`은 아무것도 참조하지 않는다.**

### `core:process`를 따로 두는 이유

adb든 simctl든 결국 "프로세스를 띄우고 줄 단위로 읽는다"로 수렴한다. 그 한 덩어리가 앱 전체에서
가장 많이 재사용되고, 동시에 제대로 테스트하기 가장 어려운 부분이다 — 프로세스 수명, 타임아웃,
취소 시 자식·손자 프로세스 회수, 파이프 드레인이 모두 여기 모인다. `data`에 묻으면 `data`가
커질수록 이 어려움이 어댑터 로직과 섞여 테스트가 통째로 무거워진다.

## `core:process` 계약

```kotlin
data class Command(
    val executable: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val workingDir: String? = null,
)

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
    /** 단발성 실행. `adb devices` 처럼 끝나는 명령. 예외를 던지지 않는다. */
    suspend fun run(command: Command, timeout: Duration): CommandResult

    /** 장수명 스트림. `logcat` 처럼 끝나지 않는 명령. */
    fun stream(command: Command, capacity: Int = 4096): Flow<CommandEvent>
}
```

계약이 지키는 것은 다섯 가지다.

**모든 실패가 값이다.** 실행 파일이 없으면 `ProcessBuilder.start()`가 `IOException`을 던지는데,
이건 exit code가 존재하지 않아 정상 종료로도 실패 종료로도 표현할 수 없다. 그런데 "Xcode 없는
기계에서 `xcrun`을 못 찾는" 경우가 이 앱의 핵심 시나리오다. 그래서 `StartFailed`를 두 계약 모두에
넣는다. `run`은 예외를 던지지 않고, `stream`은 예외를 흘리지 않는다 — `Flow<String>`이었다면
기동 실패가 raw `IOException`으로 수집부에 튀어 `core:process`의 경계가 샜을 것이다.

**타임아웃은 값이고, 프로세스를 회수한 뒤에 반환한다.** `withTimeout`이 던지는
`TimeoutCancellationException`은 `CancellationException`의 하위 타입이라, 이걸 잡아 에러로 바꾸면
"`CancellationException`은 재던진다"는 규칙과 충돌한다. 그래서 타임아웃을 예외가 아니라
`TimedOut`으로 표현한다. 또 `withTimeout`은 협조적 취소라 `readLine()`에 블록된 IO 스레드를
깨우지 못한다 — 코루틴만 빠져나오고 프로세스는 남는다. `run` 구현이 타임아웃을 감지하면 스스로
프로세스를 회수한 뒤 그때까지 읽은 출력을 실어 반환한다.

**stdout과 stderr를 동시에 드레인한다.** 둘은 별개 파이프이고 OS 버퍼가 차면 자식이 write에서
영구 블록된다. stdout만 읽는 구현은 자식이 stderr에 수십 KB를 쓰는 순간 데드락에 빠져
타임아웃까지 매달린다. `adb`는 daemon 기동 메시지를, `xcrun`은 실패 메시지를 stderr로 뱉으므로
이 앱이 다루는 바로 그 두 도구가 이 함정을 밟는다. 각각 별도 코루틴으로 읽고, `waitFor`는 그
두 리더와 동시에 돈다 — 리더가 끝나길 기다린 뒤에 `waitFor`를 시작하면 타임아웃 자체가
성립하지 않는다. `redirectErrorStream(true)`는 stderr를 따로 담아야 하는 계약과 양립하지 않는다.

**손자 프로세스까지 회수한다.** `Process.destroy()`는 직계 자식에게만 신호를 보낸다.
`adb shell ...`이나 `xcrun simctl spawn <udid> log stream`은 손자를 만들므로, 직계만 죽이면
막으려던 누수가 그대로 남는다. `ProcessHandle.descendants()`를 역순으로 destroy한 뒤 루트를
죽인다. 회수 자체는 일반 블로킹 함수라, `awaitClose`가 이미 취소된 스코프에서 실행되더라도
별도 처리 없이 그 안에서 그대로 돈다.

**스트림 유실이 조용하지 않다.** `callbackFlow`의 기본 채널 용량은 RENDEZVOUS라 수집이 한 프레임
밀리면 그 줄이 경고 없이 버려진다. logcat은 초당 수천 줄을 뿜고 로그 뷰어가 이 앱의 핵심
가치이므로, 용량을 명시하고 `SUSPEND`로 넘치면 리더가 잠깐 멎게 한다. `DROP_OLDEST`는 쓸 수
없다 — 채널이 알아서 버리면 `trySend`가 항상 성공한 것처럼 보여 몇 줄이 버려졌는지 셀 방법이
없다. 대신 리더는 카운팅 싱크를 거쳐 채널에 보낸다: 채널이 못 받으면 드롭을 세고, 다음에
받아들일 때 그 개수를 `Dropped` 이벤트로 먼저 흘려보낸 뒤 원래 이벤트를 보낸다.

그 밖에 **셸을 거치지 않는다.** `Command`는 실행 파일과 인자를 분리해 들고
`ProcessBuilder(executable, *args)`로 직접 실행한다. 문자열 하나로 합쳐 `sh -c`에 넘기면 디바이스
이름이나 경로에 공백·따옴표가 들어갈 때 깨지고, 인자에 사용자 입력이 섞이는 순간 주입 표면이
생긴다.

`executable`은 `String`이다. `java.nio.file.Path`를 쓰면 `core:process`의 공개 API 전체가
`jvmMain`에 갇히고, 그것을 참조하는 `data`의 Repository 구현까지 따라 갇힌다.

## 실행 파일 탐색과 환경

실행 파일 경로 탐색은 앱 지식이므로 `data`의 몫이다. macOS에서 Finder나 launchd로 실행된 GUI
앱은 사용자의 셸 환경을 상속하지 않는다 — `~/.zshrc`에서 export한 `ANDROID_HOME`은 없고 PATH는
`/usr/bin:/bin:/usr/sbin:/sbin` 수준이다. `./gradlew run`에서는 전부 되다가 dmg로 설치한 순간
두 도구를 다 못 찾는다. 이 스펙이 dmg 패키징을 범위에 넣었으므로 스캐폴드 단계에서 실제로 밟는다.

탐색 순서를 고정한다.

1. 앱 설정에 사용자가 직접 지정한 경로 (후속 스펙에서 설정 UI를 붙인다. 지금은 값만 읽는다)
2. 환경 변수 (`ANDROID_HOME`, `ANDROID_SDK_ROOT`, `DEVELOPER_DIR`)
3. 관례 경로 (`~/Library/Android/sdk/platform-tools/adb`, `~/Library/Android/sdk/emulator/emulator`,
   `/usr/bin/xcrun`)

찾은 값은 `Command.env`로 자식에게 물려준다. `xcrun`은 `DEVELOPER_DIR`을 보고 동작이 달라지므로
빈 환경으로 띄우면 안 된다.

## 수직 슬라이스: 디바이스 목록

```kotlin
enum class DevicePlatform { ANDROID, IOS }

/** 두 플랫폼의 상태 어휘를 공통 축으로 접는다. */
enum class DeviceState { RUNNING, STARTING, STOPPED, UNAVAILABLE }

data class Device(
    val id: String,          // 실행 중이면 adb serial 또는 simctl UDID, 정지 상태면 AVD 이름
    val name: String,
    val platform: DevicePlatform,
    val state: DeviceState,
)
```

`DeviceRepository`가 소스를 병렬로 조회해 하나의 목록으로 합친다.

**Android는 두 명령을 조인한다.** `adb devices -l`은 연결된 디바이스만 나열한다 — 부팅되지 않은
AVD는 목록에 아예 나오지 않는다. 그것만 쓰면 `DeviceState.STOPPED`가 Android 쪽에서 도달 불가능한
값이 되고, 화면이 "Android 2개 / iOS 40개"로 비대칭이 되어 좌우 비교의 의미가 사라진다. 더 중요한
것은 v0.2다 — "양쪽에 동시 설치·실행"을 하려면 정지된 AVD를 띄울 수 있어야 하는데 그 목록을 얻는
경로가 없으면 이 슬라이스가 만든 `DeviceRepository`를 다음 단계에서 그대로 못 쓴다.

그래서 `emulator -list-avds`로 전체 AVD 이름을 얻고, `adb devices -l`로 실행 중인 것을 얻어
AVD 이름 기준으로 합친다. 실행 중인 에뮬레이터의 AVD 이름은 `adb -s <serial> emu avd name`으로
얻는다. 실물 기기는 AVD 목록에 없으므로 `adb` 결과에만 나타나고 항상 `RUNNING`이다.

상태 매핑:

| 소스 | 값 | `DeviceState` |
|---|---|---|
| `emulator -list-avds`에만 있음 | — | `STOPPED` |
| `adb devices` | `device` | `RUNNING` |
| `adb devices` | `offline` | `STARTING` |
| `adb devices` | `unauthorized`, `no permissions` | `UNAVAILABLE` |
| `simctl` | `Booted` | `RUNNING` |
| `simctl` | `Booting`, `Shutting Down` | `STARTING` |
| `simctl` | `Shutdown` | `STOPPED` |
| `simctl` | 그 밖의 값 | `UNAVAILABLE` |

**simctl 결과는 걸러 쓴다.** `xcrun simctl list devices --json`은 watchOS·tvOS·visionOS 런타임까지
전부 뱉고, `isAvailable: false`인 항목(런타임이 설치되지 않은 조합)도 섞여 있다. iOS 런타임이고
`isAvailable`이 참인 것만 취한다. 그러지 않으면 목록이 수십 개로 부풀고 그중 대부분이 띄울 수
없는 항목이다. 파싱은 관대하게 — `ignoreUnknownKeys = true`로 두고, 필수 필드가 없는 항목은
그것만 건너뛰고 나머지는 살린다.

화면은 Orbit `ContainerHost`로 `DeviceListState`를 들고 두 플랫폼을 좌우 2열로 보여준다.
조작은 새로고침 하나뿐이다.

**한쪽 실패가 다른 쪽을 죽이지 않는다.** Xcode가 없는 기계에서 `xcrun`을 찾지 못했다고 Android
목록까지 못 보면 안 된다. 각 소스를 독립적으로 받아 부분 성공을 상태에 싣고, 실패한 쪽은 그
열에만 사유를 표시한다.

## 결과 타입과 에러 처리

`kotlin.Result`를 쓰지 않는다. 실패 슬롯이 `Throwable`이라 `DeviceError`를 담으려면 그것을
`Throwable`로 만들어야 하고, 그러면 `CancellationException` 재던지기 규칙과 섞여 위험해진다.
`core:common`에 자체 타입을 둔다.

```kotlin
sealed interface Outcome<out T, out E> {
    data class Ok<out T>(val value: T) : Outcome<T, Nothing>
    data class Err<out E>(val error: E) : Outcome<Nothing, E>
}
```

`domain`의 에러 갈래는 넷이다.

```kotlin
sealed interface DeviceError {
    data class ToolNotFound(val tool: String) : DeviceError
    data class ToolFailed(val tool: String, val exitCode: Int, val stderr: String) : DeviceError
    data class Timeout(val tool: String) : DeviceError
    data class ParseFailed(val tool: String, val detail: String) : DeviceError
}
```

`CommandResult`에서 `DeviceError`로의 변환은 `data` 경계에서 한 번 일어난다. `domain`과
`feature`는 프로세스라는 개념을 모른다. `CancellationException`은 잡지 않고 재던진다 — 계약이
타임아웃과 기동 실패를 값으로 표현하므로 이 규칙과 충돌하는 지점이 없다.

네 갈래로 나눈 것은 사용자에게 줄 안내가 각각 다르기 때문이다. `ToolNotFound`는 설치나 경로
설정 문제, `ToolFailed`는 도구가 뱉은 메시지를 그대로 보여줘야 하는 경우, `Timeout`은 재시도가
답인 경우, `ParseFailed`는 도구 버전이 예상과 다른 경우다. 마지막 갈래는 Xcode 버전에 따라
`simctl` 출력 스키마가 달라질 수 있다는 리스크에 대응한다.

## build-logic

루트 `settings.gradle.kts`에서 `includeBuild("build-logic")`으로 composite build로 참여시킨다.
버전은 `gradle/libs.versions.toml` 하나에 모은다.

| 플러그인 ID | 적용 대상 | 하는 일 |
|---|---|---|
| `simulcast.kmp` | 모든 모듈 (`app` 포함) | `kotlin("multiplatform")` + `jvm()` 타깃 + toolchain 21 + coroutines + `kotlin.test` |
| `simulcast.compose` | UI가 있는 모듈 | Compose Multiplatform 플러그인 + Compose 컴파일러 플러그인 |
| `simulcast.serialization` | JSON을 다루는 모듈 (`data`) | kotlinx-serialization 플러그인 + `-json` 런타임 |
| `simulcast.koin` | Koin 정의가 있는 모듈 (`data`, `feature/*`, `app`) | Koin 의존성 |
| `simulcast.feature` | `feature/*` | `simulcast.kmp`·`compose`·`koin`을 묶고 `domain`·`core:designsystem`·`core:common`·Orbit·ViewModel 배선 의존을 자동 주입 |
| `simulcast.desktop.app` | `app` | `compose.desktop.application` + `nativeDistributions`(dmg, arm64) |

이름을 `simulcast.kmp.library`가 아니라 `simulcast.kmp`로 둔다. `app`에도 적용되는데 "library"가
붙으면 이름이 거짓이 된다. `simulcast.desktop.app`은 `simulcast.kmp` 위에 얹는 것이지 대체하는
것이 아니다.

`simulcast.feature`가 domain·`core:designsystem`·`core:common`·Orbit·ViewModel 배선 의존을
자동 주입하므로, feature 모듈의 build 파일은 `plugins { }` 블록에 그 모듈에서만 필요한 의존
(예: `compose.material3`) 몇 줄을 더하는 정도로 끝난다. feature가 늘어날 때 복사해야 할
보일러플레이트가 크게 없고, "feature는 data를 모른다" 규칙이 기본값으로 지켜진다.

**컨벤션 플러그인에서 카탈로그를 읽는 방법.** precompiled script plugin에서는 타입세이프 `libs`
접근자가 생성되지 않는다. `project.extensions.getByType<VersionCatalogsExtension>().named("libs")`로
꺼내 쓰고, 그 조회를 감싸는 확장 함수 하나를 `build-logic`에 둔다. 이걸 모르면 컨벤션 플러그인
첫 작성에서 반드시 막힌다.

패키징은 `nativeDistributions`에 dmg 타깃(arm64)과 앱 이름·번들 ID까지만 설정한다. 코드 서명과
공증은 화면 기록·손쉬운 사용 권한이 필요해지는 v0.1 이후에 다룬다. 그 권한은 `.app` 번들 단위로
부여되므로, 서명·공증이 없으면 권한 다이얼로그가 앱 이름이 아니라 "Java"로 뜬다.

## 테스트 전략

- **`core:process`** — `CommandRunner` 구현은 `echo`·`sleep`·`sh -c 'yes'` 같은 실존 명령으로
  통합 테스트한다. 검증 대상이 타임아웃 시 프로세스 회수, 취소 시 손자 회수, stderr 대량 출력에도
  데드락이 없는 것, 스트림 오버플로 시 `Dropped` 보고라서 fake로는 아무것도 증명되지 않는다.
- **`data`** — `FakeCommandRunner`로 고정 출력을 주고 파싱·조인·에러 변환을 검증한다. 실제
  `adb devices -l`·`emulator -list-avds`·`simctl list --json` 출력 샘플을 픽스처로 둔다.
  `isAvailable: false`와 watchOS 항목이 섞인 픽스처를 반드시 포함한다.
- **`feature`** — `orbit-test`로 상태 전이를 검증한다. 한쪽 소스 실패 시 다른 쪽이 살아 있는지가
  핵심 케이스다.
- **DI** — Koin `verify()`는 `app` 모듈에서 합성 그래프 하나에 대해 돌린다. 모듈별로 돌리면
  `feature:devices`가 요구하는 `DeviceRepository`의 바인딩이 `app`에 있어 실패하고, 그것을
  `extraTypes`로 화이트리스트하면 정작 검증하고 싶던 타입이 검증에서 빠진다. `verify()`는 빌드
  시점이 아니라 **테스트 시점** 검사이고 **생성자 주입만** 본다 — `single { Foo(get(), "literal") }`
  처럼 람다 안에서 조립하는 정의는 사각지대다. 그 한계를 알고 쓴다.

## 리스크와 검증

**버전 조합 스파이크.** Kotlin 2.3.21 + CMP 1.11.1 + Gradle 9.7.1이 실제로 맞물리는지 구현 계획
첫 페이즈에서 확인한다. **빈 모듈 컴파일로는 아무것도 증명되지 않는다** — 빈 모듈은 Compose
컴파일러 플러그인을 거치지 않고, 이 층위의 실패는 컴파일이 아니라 런타임
`NoSuchMethodError`/`AbstractMethodError`로 드러난다. 검증은 `@Composable` 하나를 그린 창을
`./gradlew run`으로 실제 띄우고 `packageDmg`까지 돌리는 것으로 한다. 여기서 버전이 바뀌면
카탈로그와 컨벤션 플러그인이 함께 움직이므로 이 태스크가 먼저 와야 한다.

**dmg 실행 환경.** 위 스파이크에서 만든 dmg를 설치해 실제로 실행하고, `adb`·`xcrun` 탐색이
동작하는지 확인한다. `./gradlew run`에서만 확인하면 GUI 환경 변수 문제를 놓친다.

**`simctl list --json` 스키마.** Xcode 버전에 따라 필드가 달라질 수 있다. `ParseFailed` 갈래와
관대한 파싱으로 대응하되, 스키마가 크게 바뀌면 픽스처를 갱신한다.

**Koin 런타임 누락.** `app` 단위 `verify()` 테스트로 막되, 생성자 주입만 검증된다는 한계가 남는다.
람다 안에서 조립하는 정의는 리뷰에서 잡는다.

## 대응 ADR

이 스펙이 확정하는 결정 중 다음은 `docs/adr/`에 `status: proposed` 스텁으로 지금 만들고, 구현이
끝나면 `accepted`로 올린다. 코드만 보고는 대안과 기각 사유를 알 수 없는 것들이다.

- KMP multiplatform + `jvm()` 타깃 하나 (`kotlin("jvm")` 단일 플러그인 기각)
- DI로 Koin 채택 (kotlin-inject·수동 조립 기각)
- 상태관리로 Orbit MVI 채택
- `core:process`를 `data`에서 분리하고 모든 실패를 값으로 표현

## 구현 계획 페이즈

계획은 세 페이즈로 명시적으로 나눈다. 페이즈 경계가 곧 검증 지점이다.

- **A. 빌드 기반** — wrapper, 카탈로그, `build-logic` 컨벤션 플러그인, 빈 모듈 골격, 버전 조합
  스파이크(창 띄우기 + dmg 설치 실행).
- **B. `core:process`** — `CommandRunner` 계약과 구현, 통합 테스트. 이 페이즈가 끝나면 프로세스
  실행이 앱과 무관하게 혼자 선다.
- **C. 수직 슬라이스** — `domain` 모델·인터페이스, `data` 어댑터·조인·파싱, `feature:devices`
  화면, `app` Koin 조립과 `verify()`.

## 다음 단계

`superpowers:writing-plans`로 구현 계획을 만든다. 계획의 Global Constraints에 CLAUDE.md의 주석
규약 요지를 싣는다 — 코드가 이미 말하는 것은 쓰지 않기, `@return`·`@param`은 타입·이름이 말하지
못할 때만, 다른 컴포넌트의 현재 상태를 단정하지 않기.
