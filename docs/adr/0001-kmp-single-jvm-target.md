---
id: ADR-0001
title: KMP multiplatform 플러그인 + jvm() 타깃 하나
status: accepted
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
