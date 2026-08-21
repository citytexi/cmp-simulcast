# CLAUDE.md

이 저장소는 **Compose Multiplatform 기반 macOS 데스크톱 앱**(`cmp-simulcast`) 코드 저장소다.
문서·툴링은 [`docs/`](docs/README.md)에 모여 있다(`adr/`·`architecture/`·`script/`·`superpowers/`).

## 언어

항상 한국어로 답변한다.

## Git 워크플로 (필수)

**`git push`와 PR 생성·머지(`gh pr create`·`gh pr merge`) 실행 전에는 무조건 사용자에게 먼저
물어보고 확인받는다.** 사용자가 명시적으로 승인하기 전까지 이 두 작업을 자동으로 실행하지 않는다.

기준은 **리모트로 나가는가**다. 리모트에 올라간 것은 되돌리기 어렵고 남에게 보인다.
`git commit`·`git merge`는 로컬이라 확인 없이 해도 된다(코드 편집·브랜치 생성도 마찬가지).
로컬에 `PreToolUse` 훅을 두어 이 규칙을 추가로 방어할 수 있다. 다만 훅 설정 파일
(`.claude/settings.local.json`)은 추적되지 않으므로 클론마다 각자 설정해야 하고,
규칙의 정본은 이 문서다.

**`main`에 직접 커밋·푸시하지 않는다.** 모든 변경은:

1. 브랜치 생성 (`git checkout -b <설명적-브랜치명>`)
2. commit — 확인 불필요
3. push — **사용자 확인 후**
4. PR 생성 (`gh pr create`) — **사용자 확인 후**
5. `main`에 머지

개인정보(실명·연락처·주소·건강·재무·인증 토큰 등)는 커밋하지 않는다.

## 코드 구현 워크플로 (필수)

공통 진입은 `superpowers:brainstorming`(의도·요구 정리). 그다음 체인:

1. `superpowers:brainstorming` → 설계 스펙 (`docs/superpowers/specs/`)
2. `superpowers:writing-plans` → 구현 계획 (`docs/superpowers/plans/`)
3. `superpowers:subagent-driven-development` 또는 `superpowers:executing-plans` → TDD로 실행

- `writing-plans`·`test-driven-development`·`executing-plans`는 **코드 작업 전용**.
- 파일명은 `YYYY-MM-DD-kebab-topic.md`.
- 스펙이 새 아키텍처 결정을 유발하면 대응 ADR을 [`docs/adr/`](docs/adr/README.md)에 함께 만든다.
  구현 가이드("어떻게/어디")는 [`docs/architecture/`](docs/architecture/README.md).

### 스킬 적재적소 (필수)

brainstorming(스펙)·writing-plans(계획) 단계에서, 다룰 주제(Compose UI/state·recomposition·
stability·side-effects·navigation·coroutines·testing·gradle·마이그레이션 등)를
**`skill-finder`로 먼저 검색**(`python3 docs/script/search.py "<주제>"`)하고, 상위 후보 중 관련
벤더 스킬을 네이티브 `Skill`로 로드한 뒤 설계/계획을 확정한다. 전체 목차는
`.claude/skills-vendor/CATALOG.md`. 벤더 스킬 갱신은 `update-injected-skills`.

⚠️ 벤더 스킬은 Android/Jetpack Compose 전제로 쓰인 것이 많다. **이 저장소는 CMP·데스크톱이므로
적용 전에 해당 지침이 플랫폼 중립인지(Compose 런타임·코루틴·상태 관리 등) 아니면 Android 전용
API에 묶여 있는지 확인한다.**

### plan 실행 진행 표시

3단계(`subagent-driven-development`·`executing-plans`)를 시작할 때 `orca-plan-ledger`를 함께
로드한다. 태스크 경계마다 Orca 워크스페이스 카드에 진행이 찍힌다. Orca 밖(`ORCA_WORKTREE_ID`
없음)에서는 게이트에서 no-op이라 동작이 동일하다.
**가산적 장식층이다** — 원장이 실행 순서·게이트·산출물 경로를 바꾸지 않는다. 진행의 정본은
SDD의 `.superpowers/sdd/<plan>/progress.md`와 `git log`이고 카드는 그 투영이다.

## 코드 주석·KDoc

### 기준 셋

1. **코드가 이미 말하는 것은 쓰지 않는다.**
2. **뻔하지 않은 의도와 함정은 쓴다.** 왜 이 선택을 했는지, 무엇을 밟으면 깨지는지.
3. **다른 곳의 현재 상태는 쓰지 않는다** — 낡기 때문이다. 아래 [수명](#수명이-기준이다) 참고.

주석의 분량은 그 코드의 **어려움**에 비례해야지 그 코드의 **중요함**에 비례하면 안 된다.
중요한데 단순한 코드에 긴 주석을 다는 것이 가장 흔한 실수다.

### KDoc에 고정 틀을 쓰지 않는다

"의도 / 반환값 / 파라미터"를 **항상 있는 섹션으로 두지 않는다.** 빈 슬롯을 채우려는 압력이
그 자체로 노이즈를 만든다.

- **의도 한 줄**은 거의 항상 있다.
- **`@return`은 타입과 이름이 말하지 못할 때만.** `fun toColorChipType(): ColorChipType`에
  "@return 색 칩"은 순수 노이즈다.
- **`@param`은 이름이 오해를 부를 때만.**

```kotlin
/**
 * @param id 계정 id 가 아니라 그룹 멤버십 행 id 다.
 */
```

`id`는 이름이 오해를 부르니 적고, 같은 클래스의 `nickname`은 안 적는다. 그 감각이 맞다.

### 수명이 기준이다

**코드보다 빨리 낡는 주석은 아예 없는 편이 낫다.** 틀린 주석은 없는 주석보다 나쁘다 —
읽는 사람이 그것을 믿고 움직인다.

가장 잘 낡는 것은 **다른 컴포넌트의 현재 상태**를 기술한 문장이다. 서버가 무엇을 주는지,
다른 파일에 무엇이 있는지, 어느 화면이 아직 이 값을 안 읽는지. 서버 계약 한 번 바뀌면
"서버가 그쪽엔 이 값을 주지 않아서다"·"없으면 null이다"류가 하루 만에 전부 거짓이 된다.

반면 "왜 이 시간대를 붙이나"는 안 낡는다. **의도는 남고 상태는 변한다.**
써야만 한다면 낡아도 해가 없게 쓴다 — 단정 대신 근거 문서를 가리킨다.

#### 기준 2와 3이 겹칠 때는 남긴다

"다른 컴포넌트가 이 값을 **안 쓴다**"류는 상태 서술이면서 동시에 **오해를 미리 막는 함정 정보**다.
둘 중 어느 쪽인지 애매하면 **지우지 말고 포인터로 바꾼다.** 지우는 쪽이 비용이 크다 — 틀린 주석은
읽는 사람이 알아채지만, 없는 주석은 그가 이미 잘못된 길로 간 뒤에야 드러난다.

**최소 보존선**: 지울 때 그 정보가 어느 문서에 살아 있는지 확인하고, 없으면 **문서로 먼저 옮긴 뒤**
코드에 포인터를 남긴다. 코드에서 지웠는데 문서에도 없으면 그건 정리가 아니라 유실이다.

### 아키텍처 결정은 코드가 아니라 문서에 쓴다

"이 셋 중 왜 하나만 이렇게 했나", "왜 공용화하지 않았나" 같은 결정은
[`docs/architecture/`](docs/architecture/README.md)·[`docs/adr/`](docs/adr/README.md) 몫이다.
코드에는 **포인터 한 줄**만 둔다. 같은 결정 설명을 여러 파일에 복사하면 그 자체가 중복이고,
결정이 바뀔 때 전부 낡는다.

### 좋은 예

없으면 다음 사람이 같은 버그를 만드는 자리. 다만 **근거 열거는 문서 몫**이라 두 줄이면 된다.

```kotlin
// 서버는 오프셋 없는 로컬 날짜시각을 주고 그 벽시계는 KST다(docs/architecture 타임존 절).
// 오프셋을 안 붙이면 기기 타임존에 따라 다른 시점이 된다.
```

### 서브에이전트에게 실어 나른다

⚠️ **이 파일이 서브에이전트에게 자동으로 닿는다는 보장이 없다.** 구현 서브에이전트는 브리프만
읽는 경우가 많다. 그래서 **구현·리뷰 디스패치 프롬프트의 전역 제약에 이 절의 요지를 넣는다.**
최소한 이 셋:

- 코드가 이미 말하는 것은 쓰지 않는다
- `@return`·`@param`은 타입·이름이 말하지 못할 때만
- 다른 컴포넌트의 현재 상태를 단정하지 않는다(낡는다)

`writing-plans`로 계획을 쓸 때는 계획의 **Global Constraints**에도 같은 줄을 넣는다.

## 문서 규칙

- 근거는 **파일명 + 심볼명**으로. 라인번호(`파일.kt:NN`)·변동 수치(파일 수·진행률·사용 횟수) 금지.
  자세한 규칙은 [`docs/adr/README.md`](docs/adr/README.md).
- `docs/adr/`·`docs/architecture/` 문서는 YAML frontmatter 필수. 형식 권위 출처는 각 `template.md`.
- 새 문서는 해당 디렉토리 README 인덱스에 **같은 커밋으로** 한 줄 등록한다.
