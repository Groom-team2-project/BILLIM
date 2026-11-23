# 프론트엔드 AI 작업 지침

`frontend/**` 변경 시 `docs/ai/README.md`와 함께 적용한다.
디자인 규칙의 원본은 `frontend/DESIGN_SYSTEM.md`이고, 이 문서는 **작업 절차와 금지 사항**이다.

## 원본 우선순위

같은 항목이 여러 곳에 있으면 아래 순서로 따른다.

1. **확정 명세** — `docs/api/API 명세서.md`, `docs/erd/도메인 및 DB 설계.md`, `docs/policy/정책 및 상태.md`
   (상태값·카테고리·필드명·권한은 여기가 최종)
2. **프로토타입** — `docs/prototype/billim-prototype.html` (화면 구조·크기·색·문구의 최종)
3. `frontend/DESIGN_SYSTEM.md` (토큰·컴포넌트 규칙)
4. 기존 코드 패턴

명세와 프로토타입이 충돌하면 **명세를 따르고**, 어긋난 사실을 작업 보고에 적는다.

## 작업 순서

1. **인벤토리 확인** — `DESIGN_SYSTEM.md` §6-0에서 쓸 컴포넌트를 먼저 찾는다. 있으면 새로 만들지 않는다.
2. **프로토타입에서 정답 값을 추출한다.** 눈대중으로 만들지 않는다.
   ```bash
   P=docs/prototype/billim-prototype.html
   grep -nE "\.nb-<컴포넌트>" $P | grep -v "h("                        # CSS 규격
   sed -n "$(grep -n 'function <컴포넌트>' $P | cut -d: -f1),+20p" $P  # 마크업·문구
   ```
   프로토타입에는 클래스 체계가 둘 있다. 메인 탭 화면(홈·물건 찾기·상세·내 대여·채팅)은 `nb-*`(디자인 시스템),
   하위 화면(프로필·알림·등록·정책·관리자·온보딩)은 `.scr`/`.hdr`/`.card`(커스텀 화면)다.
   **작업 중인 화면이 어느 쪽인지 먼저 확인한다.**
3. 구현 → `npm run lint && npm run build`
4. 브라우저로 확인한다. 스크린샷만 보지 말고 **계산된 스타일을 실측**해 프로토타입 값과 대조한다.
   (`getComputedStyle(el).height / .padding / .fontSize / .backgroundColor`)
5. 작업 보고에 **프로토타입과 다르게 구현한 부분과 이유**를 적는다.

## 화면 골격 (직접 만들지 말 것)

| 상황 | 쓸 것 |
|---|---|
| 메인 탭 화면 (홈·찾기·상세·내 대여·채팅) | `AppLayout`이 헤더·푸터·탭바 담당. 화면은 본문만 만든다 |
| 하위 화면 (프로필·알림·정책 등) | 루트에 전역 클래스 `page-window` + 첫 요소로 `<PageHeader title="…" />` |
| AppLayout 밖 독립 화면 (온보딩·관리자) | `<PageHeader … standalone />` + 화면 자체에 창 스타일 |
| 확인·사유 입력·신고 모달, 토스트 | `useUi()` — 새 오버레이를 만들지 않는다 |
| 로딩·빈 목록·오류 | `Skeleton`/`SkeletonItem`, `EmptyState`, `ErrorState` + `useScreenState()` |

새 하위 화면을 추가하면 `AppLayout`의 `OWN_HEADER`에 경로를 등록한다(앱 헤더가 제목을 중복 표시하지 않도록).

## 상태 화면

프로토타입이 상태를 정의한 화면은 5개다. 그 화면을 건드리면 상태도 함께 유지한다.

| 화면 | 상태 |
|---|---|
| 물건 상세 | 기본 · `unavailable` 날짜 충돌 · `own` 내 물건 · `loading` · `error` |
| 내 대여 | 빌린 · 빌려준 · `conflict` 승인 충돌 · `loading` · `empty` · `error` |
| 채팅 | 목록 · 방 · 약속 잡기 · 약속 확정 · `safety` · `error` 전송 실패 · `blocked` · `loading` · `empty` (목록 오류는 `listError`) |
| 홈 | 기본 · `loading` · `empty` · `error` |
| 물건 찾기 | 목록 · 지도 · `loading` · `empty` · `error` |

목데이터 단계라 `?state=` 쿼리로 연다. API 연동 시 실제 로딩·오류로 대체한다.

## 금지 사항

- **토큰 밖 값 금지** — 색·간격·radius는 `tokens.css` 변수만. 16진수 직접 입력 금지(브랜드 색만 예외, 주석 필수).
- **전역 타이포 클래스와 모듈 크기 지정을 겹치지 않기** — 모듈에서 `font-size`를 정했으면 `t-label` 등을 같이 붙이지 않는다. 둘 다 단일 클래스라 우선순위가 불안정하다.
- **다른 컴포넌트 내부를 셀렉터로 뚫지 않기** — 해시라 매칭되지 않는다. prop이나 `className`을 추가한다.
- **CSS를 정규식으로 일괄 수정하지 않기** — `.x { }` 패턴이 `.x + .x { }` 같은 복합 선택자까지 덮어써 구분선이 사라진 사고가 있었다. 직접 편집한다.
- **빈 폴더·미사용 추상화 만들지 않기** — `services/`, `hooks/queries/`는 API 연동 때 만든다.
- **문구를 임의로 바꾸지 않기** — 프로토타입 문구가 원본. 조사는 `objectParticle()`로 처리한다.
- 명세에 있는데 화면에 없는 기능을 발견하면 **임의 구현 전에 보고**한다.

## 폴더 규칙

- 의존 방향: `pages → components → stores → data → types → (utils·constants·styles)`. 역방향 금지.
- 도메인 전용 컴포넌트는 `components/custom/<도메인>/`, 도메인 무관이면 `components/ui|layout|overlay`.
- ui 컴포넌트는 폴더 단위(`ui/<kebab>/`): PascalCase tsx + 같은 이름 module.css + `index.ts`. 임포트는 폴더 경로로 한다.
- 목데이터는 `data/<도메인>.ts`, 타입은 `types/<도메인>.ts`, 전역 상태는 `stores/`에만 둔다. 화면 로컬 상태는 useState.
- 새 라우트는 `app/App.tsx`에 추가하고, 화면은 해당 흐름의 `pages/` 폴더에 넣는다(새 흐름이면 폴더 신설 + 배럴).
- 임포트는 항상 `@/` 절대경로.

## 스타일 (CSS Modules)

- 컴포넌트 옆 같은 이름의 `X.module.css`. 사용법은 `DESIGN_SYSTEM.md` §2 참조.
- 전역 CSS는 2개뿐: `styles/tokens.css`(토큰·테마), `styles/global.css`(리셋, `t-*`·`only-*`·`sr-only`, `page-window`). 새 전역 클래스를 만들지 않는다.
- 공유 모듈: `components/overlay/overlay.module.css`, `components/custom/admin/admin.module.css` → `classes({ ...styles, ...공유 })`로 병합.
- `.map((c) => …)`처럼 헬퍼 `c`를 가리는 파라미터 이름을 쓰지 않는다.

## 백엔드 연동 시

1. `data/`의 목을 `services/`(fetch)로 교체한다. **타입은 `types/`에 그대로 두고 재사용**한다.
2. 응답·오류 형식은 `docs/api/API 명세서.md` 0장(공통 규약)과 `docs/api/에러 코드.md`를 따른다.
3. 로딩·오류는 `?state=` 대신 실제 요청 상태에 연결한다. 화면 구조는 그대로 둔다.
4. 상태값은 서버 enum을 그대로 쓴다(대여 7종, 물건 `PUBLIC`/`HIDDEN`/`DELETED`, 신고 사유 6종).
