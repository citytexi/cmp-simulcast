---
id: ADR-0004
title: core:process를 data에서 분리하고 모든 실패를 값으로 표현
status: accepted
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
