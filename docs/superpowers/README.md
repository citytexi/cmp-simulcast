# superpowers

superpowers 스킬의 산출물 기본 위치. 경로를 옮기지 않는 한 스킬이 여기에 그대로 쓴다.

| 디렉토리 | 생성 스킬 | 내용 |
|---|---|---|
| `plans/` | `superpowers:writing-plans` | 구현 계획 (`YYYY-MM-DD-<주제>.md`) |
| `specs/` | `superpowers:brainstorming` | 구현 직전 설계·요구사항 (`YYYY-MM-DD-<주제>-design.md`) |

## 참고
- 계획/설계가 새 아키텍처 결정을 유발하면 대응 ADR을 [`../adr/`](../adr/README.md)에 함께 만든다.
- 계획 수립 전 관련 벤더 스킬을 먼저 찾는다: `python3 docs/script/search.py "<주제>"` (`skill-finder`).
