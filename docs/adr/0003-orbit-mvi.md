---
id: ADR-0003
title: 상태관리로 Orbit MVI 채택
status: accepted
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
