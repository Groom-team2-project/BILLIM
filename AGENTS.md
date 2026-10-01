# BILLIM AI 작업 지침

AI 작업자가 이 저장소에서 작업할 때 따르는 진입점. 세부 규칙은 아래 문서를 원본으로 사용한다.

## 필수 문서

작업을 시작하기 전 아래 순서로 확인한다.

1. 현재 사용자의 지시와 연결된 GitHub 이슈 (있는 경우. 이슈 등록은 필수가 아니다)
2. `docs/ai/README.md` — AI 작업 절차와 권한 경계
3. `CONTRIBUTING.md` — 팀 개발 규칙
4. 변경 영역별 문서
   - `backend/**`: `docs/ai/backend.md`
   - `frontend/**`: `docs/ai/frontend.md`(작업 절차·금지 사항) → `frontend/DESIGN_SYSTEM.md`(디자인 규칙·컴포넌트 인벤토리)

## 규칙 우선순위

사용자의 명시적 지시 → GitHub 이슈(있는 경우) → `docs/ai/` → `CONTRIBUTING.md` → 기존 코드 패턴 순으로 적용한다.
충돌하거나 불명확하면 임의로 정하지 않고 사용자에게 확인한다.

## 기본 원칙

- 파일을 변경하기 전 `git status`로 기존 변경과 현재 브랜치를 확인한다.
- 현재 작업(이슈가 있으면 이슈)의 범위만 수정한다. 범위 밖 문제는 수정하지 않고 보고만 한다.
- DB 구조, 보안, 인증, API 계약 변경은 계획을 먼저 제시하고 승인 후 진행한다.
- UI 작업 전에 `docs/ai/frontend.md`와 `frontend/DESIGN_SYSTEM.md`를 읽는다. 색·간격·글꼴은 `frontend/src/styles/tokens.css` 변수만 사용한다.
- 화면을 만들기 전 `DESIGN_SYSTEM.md` §6-0 인벤토리에서 기존 컴포넌트를 먼저 찾는다. 같은 역할을 새로 만들지 않는다.
- 화면 구조·크기·문구는 `docs/prototype/billim-prototype.html`에서 값을 확인해 맞춘다. 눈대중으로 만들지 않는다.
- 커밋은 사용자가 요청한 경우에만 생성한다.
- push와 PR 생성은 실행 직전에 사용자 승인을 받는다. PR 승인과 merge는 사용자가 직접 한다.
- 코드 주석, 저장소 문서, 작업 보고는 한국어 단답식으로 작성한다.
