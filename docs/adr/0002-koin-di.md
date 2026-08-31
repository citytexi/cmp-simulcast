---
id: ADR-0002
title: DI로 Koin 채택
status: accepted
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
- 그래프 검증은 `app` 모듈에서 합성 그래프를 실제로 시작해 최상위 진입점을 해석하는 것으로 한다.

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

- `app` 단위로 합성 그래프를 시작해 최상위 진입점을 해석하는 테스트를 둔다. 어떤 진입점도
  닿지 않는 정의는 이 테스트로 잡히지 않는다 — 람다 조립 정의를 포함해 리뷰에서 잡는다.
