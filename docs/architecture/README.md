# Architecture

이 프로젝트의 구조·설계 명세 문서를 모읍니다. (모듈 구조, 상태관리 패턴, DI, 데이터 흐름, UI 흐름 등)

> **참고** — 모든 주장은 **파일명 + 심볼명(클래스/함수/프로퍼티)** 으로 근거를 표시하고, 추정은 **Assumption** 라벨을 붙입니다.
>
> **⚠ 라인번호와 수치(파일 수·진행률·사용 횟수)는 적지 않습니다** — 커밋마다 바뀌어 금방 거짓이 됩니다. 자세한 규칙은 [`../adr/README.md`](../adr/README.md) 참조.
>
> `architecture/`는 "어떻게/어디"(구현 가이드)를, `adr/`는 "왜"(결정·대안)를 다룹니다. 상호 보완입니다.

| 문서 | 내용 |
|------|------|
| [module-structure.md](module-structure.md) | 모듈 그래프, 컨벤션 플러그인별 주입 의존성, `api`/`implementation` 기준, 합성 루트(`AppModules`) 구성, 기기 목록 데이터 흐름 |

## Frontmatter (필수)

모든 architecture 문서는 YAML frontmatter를 단다(형식 권위: [`template.md`](template.md)). 필드: `id` · `title` · `category`(=architecture) · `status`(**living / superseded / deprecated**) · `platforms` · `verified`(코드 대조일) · `related_adr` · `related_architecture` · `related_code`(심볼명, 라인번호 금지) · `tags`.
