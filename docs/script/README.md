# docs/script

이 repo의 파이썬 툴링 스크립트 홈. 스킬(`.claude/skills/*`)이 호출하는 로직과 일회성 유틸을 모은다.

## 규약
- **stdlib 전용** — pip 의존성 0. `python3 docs/script/<name>.py`로 실행.
- 파일명은 기능 기반 snake/kebab(날짜 접두사 없음). 스킬 전용 로직은 스킬명과 연관되게(`vendor.py`·`search.py`).
- **경로**: repo 루트 = `Path(__file__).resolve().parents[2]`(= `docs/script/x.py` → 루트) 기준 상대. repo 이동에 무관.
- 스킬이 호출하는 스크립트는 SKILL.md에서 `python3 docs/script/<name>.py`로 참조(cwd = repo 루트).
- 테스트는 같은 디렉토리에 `test_<name>.py`(stdlib `unittest`).

## 인덱스
| 스크립트 | 용도 | 호출 스킬 |
|---|---|---|
| `vendor.py` | 소스 repo 스킬 벤더링 + baseline/diff 업데이트 | `update-injected-skills` |
| `search.py` | 벤더 스킬 자연어 검색 랭킹 | `skill-finder` |

## 테스트
```bash
cd docs/script && python3 -m unittest discover -p 'test_*.py'
```

## 템플릿
- [`_script-template.py`](_script-template.py) — 파이썬 스크립트 헤더/경로 규약.
- [`SKILL.template.md`](SKILL.template.md) — 새 스킬 `SKILL.md` frontmatter 템플릿.

## 벤더링 구조
- `.claude/skills-vendor/sources.json` — 벤더할 upstream repo 목록(SoT, 손으로 편집).
- `.claude/skills-vendor/baseline.json` — repo별 마지막 벤더 SHA(SoT). `baseline.md`는 렌더 산출물.
- `.claude/skills-vendor/manifest.json` — 스킬 leaf 이름 → {repo, 원본 경로, SHA}(SoT). `MANIFEST.md`·`CATALOG.md`는 렌더 산출물.
- `.claude/skills-vendor/licenses/` — 소스 repo LICENSE 사본.
- `.claude/skills-vendor/.cache/` — 소스 repo의 얕은 클론(gitignore).
- `.claude/skills/<leaf>/` — 벤더된 스킬 본체. 순수 사본이므로 편집 금지.

초기 설치:
```bash
python3 docs/script/vendor.py --full
```
