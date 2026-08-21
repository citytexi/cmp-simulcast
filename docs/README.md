# docs

이 repo의 문서·툴링 홈. 필요한 디렉토리만 펼쳐 읽는다.

| 디렉토리 | 다루는 것 |
|---|---|
| [`adr/`](adr/README.md) | **왜** 이렇게 결정했는가 — 아키텍처 결정 기록(대안·트레이드오프 포함) |
| [`architecture/`](architecture/README.md) | **어떻게/어디** — 상시 갱신되는 구현 가이드(모듈 구조·상태관리·데이터 흐름 등) |
| [`script/`](script/README.md) | 파이썬 툴링 스크립트(스킬 벤더링 `vendor.py`, 스킬 검색 `search.py`) |
| [`superpowers/`](superpowers/README.md) | superpowers 스킬 산출물 — `plans/`(구현 계획)·`specs/`(브레인스토밍 설계) |

## 공통 규칙
- 근거는 **파일명 + 심볼명**으로. 라인번호(`파일.kt:NN`)와 변동 수치(파일 수·진행률·사용 횟수)는 적지 않는다 — 자세한 규칙은 [`adr/README.md`](adr/README.md).
- 모든 `adr/`·`architecture/` 문서는 YAML frontmatter 필수. 형식 권위 출처는 각 디렉토리의 `template.md`.
