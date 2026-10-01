# BILLIM 프론트엔드 디자인 시스템

> 이 문서는 `frontend/`에서 UI를 만드는 **모든 사람과 AI 도구**(Claude, Cursor, Copilot, ChatGPT 등)가 따르는 기준이에요.
> AI에게 화면을 만들게 할 때는 이 파일을 먼저 읽히고 시작하세요. 이 문서와 다른 방식으로 구현해야 하면 PR에 이유를 적어 주세요.

**AI 도구에게:** UI 코드를 쓰기 전에 이 문서 전체를 읽어. 규칙 중 **MUST**는 예외 없이 지키고, 이 문서에 없는 색·글꼴·그림자·간격 값을 새로 만들지 마. 필요한 컴포넌트가 없으면 가장 가까운 컴포넌트를 조합하고, 새 컴포넌트를 만들었다면 §6 목록 형식으로 이 문서에 추가를 제안해.

## 목차

1. [서비스와 화면 원칙](#1-서비스와-화면-원칙)
2. [기술 전제와 폴더 구조](#2-기술-전제와-폴더-구조)
3. [디자인 토큰](#3-디자인-토큰)
4. [타이포그래피](#4-타이포그래피)
5. [대여 상태](#5-대여-상태)
6. [컴포넌트](#6-컴포넌트)
7. [레이아웃과 내비게이션](#7-레이아웃과-내비게이션)
8. [화면 명세](#8-화면-명세)
9. [로딩·빈 결과·오류·충돌](#9-로딩빈-결과오류충돌)
10. [문구 규칙](#10-문구-규칙)
11. [접근성](#11-접근성)
12. [하지 말 것](#12-하지-말-것)

---

## 1. 서비스와 화면 원칙

BILLIM은 같은 동네·아파트 단지 같은 생활권 커뮤니티 안에서 전동드릴·캐리어·캠핑 의자처럼 가끔 쓰는 물건을 **필요한 날짜에 무료로** 빌리고 빌려주는 서비스예요. 결제·보증금·배송은 없어요.

| # | 원칙 (MUST) | 구현에서 의미하는 것 |
|---|---|---|
| P1 | 날짜·거래 장소·대여 상태는 항상 보인다 | 물건/대여/채팅 카드에 `DateRange`, 장소 줄, `StatusBadge`가 빠지지 않음 |
| P2 | 집 주소를 묻지 않는다 | 주소 입력 필드 금지. 거래 장소는 커뮤니티 **공용 장소 목록**에서 선택 |
| P3 | 모든 화면에 로딩·빈 결과·오류 상태 | §9의 세 상태를 화면마다 구현 |
| P4 | 막히면 이유와 다음 행동을 보여준다 | 예약 충돌로 승인 실패 시 누구의 어떤 기간과 겹치는지 + 버튼 |
| P5 | 신뢰는 점수가 아니라 근거 문장 | "반납 약속 12/12 지킴", "거래 완료 18회". 별점·등급·% 금지 |
| P6 | 가능 여부는 조회 시점 기준 | 검색·상세에 "승인할 때 다시 확인해요" 안내 |
| P7 | 무료 원칙 | 채팅에서 돈·보증금·계좌 이야기가 나오면 경고 + 신고 |

---

## 2. 기술 전제와 폴더 구조

- React 19 + TypeScript + Vite. 스타일은 **CSS Modules + 토큰 변수**. 색·간격·글꼴은 `src/styles/tokens.css` 변수만 쓴다.
- 지도는 카카오맵 SDK 예정. 현재는 `MapView`(단지 도식)로 대체.
- 모바일 우선. 브레이크포인트 **760px** 하나(§7).
- 임포트는 항상 `@/` 절대경로(`vite.config.ts`·`tsconfig.app.json` alias).

```
frontend/src/
  main.tsx                  진입점
  app/App.tsx               라우터 + UiProvider
  pages/                    라우트 화면 (흐름 단위 폴더 + index.ts)
    home/ search/ items/ register/ chat/ rentals/ notifications/
    profile/ neighbors/ policy/ onboarding/ admin/
  components/
    ui/<kebab>/             원자 컴포넌트 — tsx + module.css + index.ts
    layout/                 base(AppLayout) · header · nav · footer
    overlay/                UiProvider (확인·사유·신고 모달, 토스트)
    custom/<도메인>/         도메인 컴포넌트 (items·rentals·chat·community·profile·policy·admin)
  types/                    도메인 타입 (item·rental·chat·notification·admin·policy)
  data/                     목데이터 (API 연동 시 이 계층만 교체)
  stores/                   전역 상태 (session·theme·items·community·notifications·admin)
  hooks/  utils/  constants/
  styles/tokens.css         §3 값의 유일한 출처
  styles/global.css         리셋 · t-* 타이포 · only-desk/only-mobile · sr-only
```

### CSS Modules 사용법

```tsx
import styles from "@/components/ui/button/Button.module.css";
import { classes } from "@/utils/classes";
const c = classes(styles);

<button className={c("btn btn--primary")} />        // 모듈 클래스 → 해시
<span className={c("only-desk")} />                 // 전역 유틸 → 그대로 통과
```

- 모듈에 없는 이름은 그대로 통과하므로 전역 유틸(`only-desk`, `sr-only`)을 같이 쓸 수 있다.
- **모듈이 글꼴 크기를 지정한 요소에 전역 `t-*` 클래스를 같이 붙이지 않는다.** 둘 다 단일 클래스라 우선순위가 불안정해 크기가 뒤집힌다.
- 다른 컴포넌트 내부를 셀렉터로 뚫지 않는다(해시라 매칭 안 됨). 필요하면 그 컴포넌트에 `className`이나 prop을 추가한다.
- `.map((c) => …)`처럼 헬퍼 `c`를 가리는 파라미터 이름을 쓰지 않는다.
- 여러 화면이 쓰는 공유 모듈: `components/overlay/overlay.module.css`, `components/custom/admin/admin.module.css` → `classes({ ...styles, ...공유 })`로 병합.

## 3. 디자인 토큰

아래 코드블록을 `src/styles/tokens.css`로 그대로 옮기세요. **컴포넌트 코드에 16진수 색·px 간격을 직접 쓰지 않아요.** 토큰에 없는 값이 필요하면 팀에 먼저 제안하세요.

```css
@font-face {
  font-family: "Pretendard Variable";
  src: url("/fonts/PretendardVariable.woff2") format("woff2");
  font-weight: 45 920;
  font-display: swap;
}
/* Array(로고 전용) 파일이 준비되면 같은 방식으로 @font-face 추가 */

:root, [data-theme="light"] {
  --black-b: #10100e;
  --gray-d3: #191815;
  --gray-d2: #30302b;
  --gray-d1: #606055;
  --gray-b: #c0c0ab;
  --gray-l1: #e8e8cf;
  --gray-l2: #f8f8dd;
  --white-b: #ffffe3;
  --bg: var(--gray-l2);
  --surface: var(--white-b);
  --surface-sunken: var(--gray-l1);
  --line: var(--gray-b);
  --line-control: #8a8a7a;
  --ink: var(--black-b);
  --ink-muted: var(--gray-d1);
  --action: var(--black-b);
  --on-action: var(--white-b);
  --focus: var(--black-b);
  --scrim: rgba(16,16,14,0.40);
  --status-requested-fg: #4f3f94;
  --status-requested-bg: #e6e1f5;
  --status-requested-mark: #7b6cc4;
  --status-approved-fg: #1f5a8f;
  --status-approved-bg: #dce8f4;
  --status-approved-mark: #4d86bd;
  --status-active-fg: #2b6436;
  --status-active-bg: #d8ebd5;
  --status-active-mark: #4f9160;
  --status-returned-fg: #4d5057;
  --status-returned-bg: #e3e4e6;
  --status-returned-mark: #80848c;
  --status-overdue-fg: #a1321a;
  --status-overdue-bg: #f8dcd2;
  --status-overdue-mark: #d4593a;
  --status-rejected-fg: #8a2f57;
  --status-rejected-bg: #f3dae5;
  --status-rejected-mark: #c0578a;
  --status-canceled-fg: #606055;
  --status-canceled-bg: #f8f8dd;
  --status-canceled-mark: #8a8a7a;
  --shadow-float: 0 6px 20px rgba(16,16,14,0.08);
}

[data-theme="dark"] {
  --bg: var(--black-b);
  --surface: var(--gray-d3);
  --surface-sunken: var(--gray-d2);
  --line: var(--gray-d2);
  --line-control: #77776a;
  --ink: var(--white-b);
  --ink-muted: var(--gray-b);
  --action: var(--white-b);
  --on-action: var(--black-b);
  --focus: var(--white-b);
  --scrim: rgba(0,0,0,0.60);
  --status-requested-fg: #cdc4f2;
  --status-requested-bg: #2a2540;
  --status-requested-mark: #8f82d6;
  --status-approved-fg: #b8d4f0;
  --status-approved-bg: #1b2a3a;
  --status-approved-mark: #5f97cc;
  --status-active-fg: #b6e0bb;
  --status-active-bg: #1c2e1f;
  --status-active-mark: #62a371;
  --status-returned-fg: #c9ccd2;
  --status-returned-bg: #26282c;
  --status-returned-mark: #8a8e96;
  --status-overdue-fg: #f5b7a5;
  --status-overdue-bg: #3d1f17;
  --status-overdue-mark: #e0704f;
  --status-rejected-fg: #efb6cf;
  --status-rejected-bg: #3a1d29;
  --status-rejected-mark: #cf6d9b;
  --status-canceled-fg: #c0c0ab;
  --status-canceled-bg: #191815;
  --status-canceled-mark: #77776a;
  --shadow-float: 0 6px 20px rgba(0,0,0,0.45);
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: var(--black-b);
    --surface: var(--gray-d3);
    --surface-sunken: var(--gray-d2);
    --line: var(--gray-d2);
    --line-control: #77776a;
    --ink: var(--white-b);
    --ink-muted: var(--gray-b);
    --action: var(--white-b);
    --on-action: var(--black-b);
    --focus: var(--white-b);
    --scrim: rgba(0,0,0,0.60);
    --status-requested-fg: #cdc4f2;
    --status-requested-bg: #2a2540;
    --status-requested-mark: #8f82d6;
    --status-approved-fg: #b8d4f0;
    --status-approved-bg: #1b2a3a;
    --status-approved-mark: #5f97cc;
    --status-active-fg: #b6e0bb;
    --status-active-bg: #1c2e1f;
    --status-active-mark: #62a371;
    --status-returned-fg: #c9ccd2;
    --status-returned-bg: #26282c;
    --status-returned-mark: #8a8e96;
    --status-overdue-fg: #f5b7a5;
    --status-overdue-bg: #3d1f17;
    --status-overdue-mark: #e0704f;
    --status-rejected-fg: #efb6cf;
    --status-rejected-bg: #3a1d29;
    --status-rejected-mark: #cf6d9b;
    --status-canceled-fg: #c0c0ab;
    --status-canceled-bg: #191815;
    --status-canceled-mark: #77776a;
    --shadow-float: 0 6px 20px rgba(0,0,0,0.45);
  }
}

:root {
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;
  --space-12: 48px;
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 24px;
  --radius-full: 999px;
  --size-touch: 44px;
  --size-header: 56px;
  --size-tabbar: 64px;
  --size-content: 1120px;
  --font-sans: "Pretendard Variable", Pretendard, -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Noto Sans KR", system-ui, sans-serif;
  --font-logo: Array, "Pretendard Variable", Pretendard, system-ui, sans-serif;
}
```

### 3-0. 색 테마와 다크 모드

배경 팔레트는 5종이며 사용자가 헤더의 `ThemeMenu`로 바꾼다. 다크 모드는 팔레트와 무관한 단일 톤이다.

| 테마 | 루트 클래스 | 대표색 |
|---|---|---|
| 크림 (기본) | *(클래스 없음)* | `#f8f8dd` |
| 화이트 | `.theme-white` | `#f8f8f8` |
| 블루 | `.theme-blue` | `#dcf5f5` |
| 레드 | `.theme-red` | `#f5e6e6` |
| 그린 | `.theme-green` | `#dcf6e0` |

- 테마는 `<html>`의 클래스, 다크는 `<html data-theme="dark">`로 적용한다(`stores/theme.ts`, localStorage 저장).
- 팔레트가 바꾸는 것은 **그레이 스케일 8개**(`--black-b` ~ `--white-b`)뿐이다. 역할 토큰(`--bg`·`--surface`·`--ink` …)과 상태색은 그대로 두므로, **역할 토큰만 쓰면 모든 테마에서 자동으로 맞는다.**
- 그래서 그레이 스케일 변수를 컴포넌트에서 직접 쓰지 않는다. 항상 역할 토큰을 쓴다.

### 3-1. 색 사용 규칙

크림 톤 흑백이 기본이고, **색이 있는 건 대여 상태뿐**이에요. 강조색이 따로 없어요.

| 토큰 | 라이트 | 쓰는 곳 |
|---|---|---|
| `--bg` | gray-l2 `#f8f8dd` | 페이지 바탕 |
| `--surface` | white-b `#ffffe3` | 카드, 헤더, 탭바, 시트 |
| `--surface-sunken` | gray-l1 `#e8e8cf` | 입력·검색창 배경, 사진 자리, 스켈레톤, 정보 박스 |
| `--line` | gray-b `#c0c0ab` | 카드 테두리·구분선 (장식용) |
| `--line-control` | `#8a8a7a` | 입력·칩·보조 버튼 테두리 (3:1 확보용으로 추가한 값) |
| `--ink` | black-b `#10100e` | 본문·제목 |
| `--ink-muted` | gray-d1 `#606055` | 보조 텍스트 (bg·surface·sunken 위 4.5:1 이상) |
| `--action` / `--on-action` | 먹 / 크림 | 주 버튼, 선택된 칩·탭, 내 말풍선 |
| `--focus` | 먹 | 포커스 링 |

- `--gray-b`는 라이트 테마에서 **텍스트 색으로 쓰지 않아요**(대비 부족).
- 다크 테마는 `data-theme="dark"` 또는 시스템 설정으로 자동 전환돼요. 컴포넌트는 의미 토큰(`--surface`, `--ink` …)만 쓰면 다크 대응이 끝나요. **원시 팔레트(`--gray-l1` 등)를 컴포넌트에서 직접 쓰지 마세요.**

### 3-2. 간격·모양

| 토큰 | 값 | 쓰는 곳 |
|---|---|---|
| `--space-4` | 16px | 모바일 좌우 여백, 카드 패딩 |
| `--space-5` | 20px | 넉넉한 카드 패딩 |
| `--space-6` / `--space-8` | 24 / 32px | 섹션 사이 (모바일 / 데스크톱) |
| `--radius-sm` | 8px | 배지, 작은 썸네일 |
| `--radius-md` | 12px | 버튼, 입력 |
| `--radius-lg` | 16px | 카드, 사진 |
| `--radius-xl` | 24px | 바텀시트 |
| `--radius-full` | 999px | 칩, 검색창, 아바타 |
| `--shadow-float` | — | **바텀시트·드롭다운에만.** 카드는 1px `--line` 테두리만 |
| `--size-touch` | 44px | 모든 터치 대상 최소 크기 |

---

## 4. 타이포그래피

- 본문·UI: **Pretendard Variable** (`--font-sans`) 하나만.
- 로고: **Array** (`--font-logo`) — 워드마크에만. 제목·버튼·본문에 쓰지 않아요. 로고는 가능하면 이미지 파일(`billim-wordmark`)을 쓰세요.
- `body { word-break: keep-all; }` — 한국어 단어가 중간에 끊기지 않게.

| 이름 | 크기/행간 | 굵기 | 쓰는 곳 |
|---|---|---|---|
| `display` | 28/36 | 700, -0.02em | 홈 인사말, 한 화면에 하나 |
| `title-lg` | 22/30 | 700 | 상세 제목, 데스크톱 페이지 제목 |
| `title` | 18/26 | 700 | 섹션 제목, 헤더 타이틀 |
| `body-lg` | 16/24 | 500 | 날짜·장소 같은 핵심 정보, 카드 제목 |
| `body` | 15/23 | 400 | 설명, 채팅 말풍선 |
| `label` | 14/20 | 600 | 버튼, 칩, 탭, 입력 라벨 |
| `caption` | 13/18 | 500 | 보조 정보, 힌트, 신뢰 근거 |
| `micro` | 12/16 | 600 | 상태 배지, 하단 탭 라벨, 카운트 |

---

## 5. 대여 상태

상태 이름·색·아이콘은 화면·API·DB에서 **같은 값**을 써요. 색만으로 구분하지 않고 **색 + 아이콘 + 글자**를 항상 함께 보여 줘요.

```ts
// src/features/rentals/status.ts
export type RentalStatus =
  | 'REQUESTED' | 'APPROVED' | 'ACTIVE' | 'RETURNED'   // 진행
  | 'OVERDUE'   | 'REJECTED' | 'CANCELED';             // 예외

export const STATUS_UI: Record<RentalStatus, { label: string; icon: string; token: string }> = {
  REQUESTED: { label: '요청',      icon: 'ring',     token: 'requested' }, // 보라
  APPROVED:  { label: '승인',      icon: 'check',    token: 'approved'  }, // 파랑
  ACTIVE:    { label: '대여 중',   icon: 'swap',     token: 'active'    }, // 초록 (겹친 두 원)
  RETURNED:  { label: '반납 완료', icon: 'returned', token: 'returned'  }, // 차가운 회색
  OVERDUE:   { label: '연체',      icon: 'alert',    token: 'overdue'   }, // 주홍
  REJECTED:  { label: '거절',      icon: 'x',        token: 'rejected'  }, // 장미
  CANCELED:  { label: '취소',      icon: 'dash',     token: 'canceled'  }, // 크림 + 점선 테두리
};
// 배지:        color: var(--status-<token>-fg); background: var(--status-<token>-bg);
// 진행 막대·점: background: var(--status-<token>-mark);   (글자에는 -mark 쓰지 않기)
```

`OVERDUE`가 서버 상태가 아니라 "ACTIVE + 반납일 지남"으로 계산된다면, 화면에서만 OVERDUE로 바꿔 보여 주세요.

### 5-1. 역할별 행동 버튼 (RentalCard)

| 상태 | 빌리는 사람 | 빌려주는 사람(소유자) |
|---|---|---|
| 요청 | [요청 취소] | [거절] [승인] |
| 승인 | [요청 취소] [장소 보기] | [전달 확인] |
| 대여 중 | 안내만: "반납은 ○○ 님이 확인해요" | [반납 확인] |
| 연체 | 안내만 | [반납 확인] + "반납 전까지 새 승인·전달이 막혀요" |
| 거절 | [다른 날짜로 다시 요청] + 거절 사유 | — |
| 반납 완료·취소 | 버튼 없음 | 버튼 없음 |

- 전달·반납 확인은 **소유자만** 해요. 기한이 지나도 자동 반납되지 않는다고 안내하세요.
- 진행 막대(`Steps`): 요청 → 승인 → 대여 중 → 반납. 지나온 칸은 각 단계의 `-mark` 색(보라 → 파랑 → 초록 → 회색). 연체면 현재 칸이 주홍 + "반납 지연".

### 5-2. 약속 상태 (채팅)

| 상태 | 배지 | 색 토큰 |
|---|---|---|
| 약속 제안 | ○ 약속 제안 | `requested` |
| 약속 확정 | ✓ 약속 확정 | `approved` |
| 변경 요청 | ↻ 변경 요청 | `overdue` |

---

## 6. 컴포넌트

화면은 **아래 목록을 조합해서** 만든다. 같은 역할의 것을 새로 만들지 않는다.

### 6-0. 구현 인벤토리 (실제 import 경로)

새 화면을 만들기 전에 이 표에서 먼저 찾는다. 없을 때만 새로 만들고, 만들면 이 표에 추가한다.

| 임포트 | 제공 |
|---|---|
| `@/components/ui/button` | `Button` |
| `@/components/ui/icon` | `Icon` (프로토타입 아이콘 41종, 이름 체계 동일) |
| `@/components/ui/chip` · `chip-scroller` | `Chip`, `ChipScroller`(드래그·좌우 버튼 가로 스크롤) |
| `@/components/ui/segmented` | `Segmented` |
| `@/components/ui/text-field` · `search-field` | `TextField`, `SearchField` |
| `@/components/ui/calendar` | `Calendar` (실날짜·월 이동·기간 선택) |
| `@/components/ui/alert` | `Alert` (`info`/`warning`/`danger`, `actions`) |
| `@/components/ui/state` | `EmptyState`, `ErrorState`, `Skeleton`, `SkeletonItem`, `Loading` |
| `@/components/ui/avatar` · `meta` · `d-day` · `date-range` · `section-head` · `place-card` | `Avatar`, `Meta`, `DDay`, `DateRange`, `SectionHead`, `PlaceCard` |
| `@/components/ui/more-menu` · `theme-menu` | `MoreMenu`(⋮ 드롭다운), `ThemeMenu` |
| `@/components/layout/header` | `PageHeader` (하위 화면 헤더), `Header` |
| `@/components/custom/items` | `ItemCard`, `Photo`, `PhotoSlider`, `CategoryGrid`, `MapView` |
| `@/components/custom/rentals` | `RentalCard`, `StatusBadge`, `Steps` |
| `@/components/custom/chat` | `ChatRoom`, `ChatListItem`, `Bubble`, `Composer`, `ContextBar`, `AppointmentCard`, `AppointmentSheet` |
| `@/components/custom/profile` | `OwnerCard`, `TrustLines` |
| `@/components/custom/community` | `CommunitySheet` (내 동네 설정) |
| `@/components/overlay/UiContext` | `useUi()` → `confirm` · `reason` · `report` · `toast` |
| `@/hooks/useScreenState` | `useScreenState()` → `?state=` 로 로딩·빈·오류 확인 |
| `@/stores/*` | `useSession`·`login`/`logout`, `useTheme`, `useMyItems`·`setVisibility`·`deleteItem`, `useCommunities`, `useNotifications`·`markAllRead`, `useReports`·`useNotices` |
| `@/data/*` | 목데이터 (`items`·`rentals`·`chat`·`notifications`·`community`·`admin`·`policy`) |
| `@/utils/*` | `classes`, `fmtDate`·`md`·`rangeDays`, `objectParticle`(을/를) |

**직접 만들지 말 것** — 모달·토스트(`useUi()` 사용), 하위 화면 헤더(`PageHeader`), 빈·오류·로딩 화면(`state`), 가로 스크롤 칩 줄(`ChipScroller`), 아이콘(`Icon`에 추가).


### 기본

| 컴포넌트 | 주요 props | 규칙 |
|---|---|---|
| `Button` | `variant: primary \| secondary \| ghost \| danger`, `size: sm(36) \| md(44) \| lg(52)`, `icon`, `block`, `disabled` | 한 화면(카드)에 primary 하나. 거절은 secondary, danger는 신고·차단에만. `loading`은 아직 미구현 |
| `TextField` | `label`, `hint`, `optional`, `multiline`, `placeholder`, `defaultValue` | 라벨 항상 표시(placeholder로 대체 금지). `error`는 아직 미구현 |
| `SearchField` | `placeholder`, `className` | 알약형, `--surface-sunken` 배경 |
| `Chip` | `selected`, `icon`, `count`, `caret`, `onClick` | 필터용. 날짜 칩은 선택된 기간을 그대로 표기 |
| `ChipScroller` | `label` | 칩 줄이 넘칠 때 드래그 스크롤 + 좌우 이동 버튼 + 가장자리 페이드 |
| `Segmented` | `label`, `value`, `options`, `onChange` (제네릭) | 정렬(최신순/가까운 순), 보기(목록/지도) |
| `MoreMenu` | `label`, `items: {label, icon?, danger?, onSelect}[]` | ⋮ 드롭다운. 하위 화면 헤더·채팅방에서 사용 |
| `Icon` | `name`, `size`, `strokeWidth` | 프로토타입 아이콘 41종과 이름·패스 동일. 이모지 대신 사용 |

### 대여 정보

| 컴포넌트 | 주요 props | 규칙 |
|---|---|---|
| `StatusBadge` | `status` | §5 매핑 그대로. 높이 24, `--radius-sm` |
| `Steps` | `status` | 4칸 진행 막대 |
| `DateRange` | `start`, `end` | 형식 고정: `9/26(토) – 9/27(일) · 2일` (시작·반납일 포함 일수) |
| `DDay` | `late` | `받기 D-2`, `반납 D-1`, 지나면 `반납 1일 지남`(주홍) |
| `PlaceCard` | `place`, `detail`, `distance` | "거래 장소 · 공용 장소" 라벨 + "집 주소는 서로 공유되지 않아요" |
| `OwnerCard` / `TrustLines` | `trust: {kept,total,late,done,since}` | 근거 문장만. 기록 없으면 "아직 빌린 기록이 없어요" |
| `Calendar` | `today: Date`, `from`/`to: Date`, `booked: [Date,Date][]`, `value: [Date,Date] \| null`, `onChange` | 실제 날짜 계산 · 월 이동 · 기간 선택. 날짜 상태 5종: 선택 / 가능 / 예약됨(빗금+취소선) / 지난·기간 외 / 오늘(테두리). 범례 항상 표시. 각 날짜 `aria-label`에 상태 |

### 목록·카드

| 컴포넌트 | 규칙 |
|---|---|
| `ItemCard` (`grid` \| `row`) | 사진 → 제목 → 장소(·거리) → 가능 여부(아이콘+문구). row는 소유자·반납 약속·등록 시점까지. 사진 없으면 카테고리 아이콘 자리표시 |
| `RentalCard` | 제목+배지 → 상대방 → [날짜·D-day / 장소 / 확정된 약속] 박스 → Steps → 안내 → 버튼(§5-1). 충돌이면 버튼 대신 danger Alert |
| `ChatListItem` | 아바타 · 이름+시간 · 마지막 메시지 · 상태 배지+약속 · 물건 썸네일+안 읽은 수 |
| `PhotoSlider` | 물건 상세 사진 캐러셀. 스와이프·드래그·좌우 화살표·점 인디케이터 |
| `MapView` | 단지 도식 + 공용 장소 핀(`selected`, `onSelect`). 핀은 알약형 `[개수] 장소명`, 선택 시 action 색. 사람·집 위치 핀 금지 |

### 피드백

| 컴포넌트 | 규칙 |
|---|---|
| `Alert` (`info` \| `warning` \| `danger`) | 배경 톤으로만 구분(왼쪽 색 띠 금지). danger는 이유 + 행동 버튼 필수 |
| `EmptyState` | 아이콘 원 + 제목(조건을 되풀이) + 설명 + 조건을 푸는 버튼 |
| `ErrorState` | "불러오지 못했어요" + [다시 시도]. 입력·필터 유지 안내 |
| `Skeleton` | 실제 레이아웃과 같은 자리·크기. `prefers-reduced-motion`이면 애니메이션 끔 |

### 채팅

| 컴포넌트 | 규칙 |
|---|---|
| `ContextBar` | 채팅방 상단 고정: 물건 썸네일 · 제목 · StatusBadge · 기간 · 장소 · [약속 잡기] |
| `Bubble` | 상대: surface + line 테두리. 나: action 채움. 시스템 메시지는 가운데 알약. 실패: 주홍 점선 + [다시 보내기][삭제] |
| `AppointmentCard` | 종류(전달/반납) · 날짜 · 시간 · 공용 장소 + §5-2 배지. 받은 제안 [시간 바꾸기][수락], 확정 [캘린더에 추가][변경 요청] |
| `AppointmentSheet` | 종류 → 시간 칩 → 공용 장소 라디오 → "9/26(토) 오전 10:00 약속 보내기". 날짜는 대여 시작일/반납일로 고정 |
| `Composer` | [+] 입력창 [보내기]. 차단 상태면 입력 대신 안내 |

---

## 7. 레이아웃과 내비게이션

| | 모바일 (< 760px) | 데스크톱 (≥ 760px) |
|---|---|---|
| 헤더 | 56px. 홈=워드마크 / 탭 화면=왼쪽 제목 / 하위 화면=뒤로+가운데 제목+더보기 | 68px. 워드마크 · [홈 · 물건 찾기 · 채팅 · 내 대여] · 검색 · [물건 등록] · 알림 · 프로필 |
| 내비 | 하단 탭 64px: **홈 / 물건 찾기 / 등록 / 채팅 / 내 대여** (등록은 채워진 버튼) | 헤더 내비. 하단 탭 없음 |
| 알림·프로필 | 헤더의 종·아바타 | 동일 |
| 본문 | 좌우 16px, 섹션 간 24px | 최대 1120px 가운데, 좌우 24px, 섹션 간 32px, 오른쪽 360px 고정 칼럼 |
| 결정 화면 | 상세·요청·채팅방은 탭 대신 **하단 액션 바**(선택한 날짜·장소 + 주 버튼) 또는 입력창 | 오른쪽 sticky 카드 |
| 푸터 | 홈 맨 아래에만 짧게 | 모든 화면. 무료 원칙 문구 + 서비스·도움 링크 |

컨테이너 쿼리(`container-type: inline-size`)나 미디어 쿼리 둘 다 괜찮지만 기준값은 760px 하나로 통일해요.

---

## 8. 화면 명세

각 화면은 §9의 로딩·빈 결과·오류 상태를 포함해야 해요. (★ = 디자인 시스템에 시안 있음)

| # | 화면 | 핵심 구성 | 추가 상태 |
|---|---|---|---|
| 1 | 로그인 | 워드마크, 한 줄 소개, 카카오 버튼 | 로그인 실패 |
| 2 | 동네 가입 | 우리 동네 찾기(검색·현재 위치) → 동네 인증(반경 확인) → 가입 완료. "정확한 좌표·집 주소는 저장하지 않아요" | 반경 밖 인증 실패, 위치 권한 거부 |
| 3 ★ | 홈 | 커뮤니티 칩 · 인사말 · 검색 · 카테고리 5종 · 내 대여 현황(요청 대기/승인/대여 중 + 가장 급한 한 건) · 최신 물건 | 빈 커뮤니티 → 첫 물건 등록 |
| 4 ★ | 물건 찾기 | 검색 · 날짜 칩(맨 앞) · 카테고리 · 최신순/가까운 순 · 목록/지도 · 결과 수(aria-live) · 조회 시점 안내 | 지도 보기, 날짜 선택 시트 |
| 5 ★ | 물건 상세 | 사진 · 제목·가능 여부 · 설명 · PlaceCard · Calendar · OwnerCard · [채팅] [대여 요청] · 신고 링크 | 날짜 충돌, 내 물건(수정/요청 보기), 공개 중지 |
| 6 | 대여 요청 | 날짜 선택 시트 → 확인(물건·기간·장소·소유자) → 요청 완료 | 선택 날짜 충돌 |
| 7 | 물건 등록 | 사진(최대 5) · 제목 · 카테고리 · 설명 · 공용 장소 선택 · 대여 가능 기간 → 미리보기 → 완료 | 사진 용량 초과, 필수값 누락 |
| 8 ★ | 내 대여 | 빌린/빌려준 탭(건수) · 상태 필터 칩 · RentalCard(연체 → 요청 → 승인 → 대여 중 → 완료 순) | 승인 충돌 |
| 9 | 알림 | 요청·승인·거절·취소·반납·약속 알림, 안 읽음 표시, 모두 읽음, 탭하면 해당 대여/채팅 | — |
| 10 | 내 프로필 | 신뢰 근거 문장 · 등록한 물건(공개 중/중지) · 차단 목록 · 신고 내역 · 로그아웃. 타인 프로필엔 신고·차단 | — |
| 11 ★ | 채팅 | 목록 → 채팅방(ContextBar 고정, 시스템 메시지) → 약속 잡기 → 확정 | 안전 안내, 전송 실패, 차단 |
| 12 | 관리자 | 대시보드(커뮤니티 선택·KPI·주간 차트) · 신고 관리(경고/일시정지/반려) · 공지 관리(작성·삭제·고정) | 권한 없음 |
| 13 | 이웃 프로필 | 신뢰 근거 · 등록한 물건 · [신고] [차단] | — |
| 14 | 차단한 이웃 / 신고 내역 | 프로필 하위. 차단 해제 · 접수 상태(확인 중/처리 완료) | 빈 목록 |
| 15 | 내 동네 설정 / 동네 추가 | 홈 동네 버튼 → 바텀시트(최대 2개, 전환·삭제) → 동네 추가(검색·최근·근처) → 위치 인증 | 최소 1개 유지 |
| 16 | 정책·안내 | 푸터 링크. 허브(서비스 소개·고객센터) + 본문 6종 + 작성 전 자리 표시 | — |

실제 라우트는 `src/app/App.tsx`가 원본이다.

---

## 9. 로딩·빈 결과·오류·충돌

| 상태 | 구현 |
|---|---|
| 로딩 | 실제 레이아웃 자리에 `Skeleton`. 컨테이너에 `role="status"` + 숨김 문구 "불러오는 중". 3초 넘으면 문구 추가 |
| 빈 결과 | `EmptyState`. 제목은 조건을 그대로: "9/26 – 9/27에 빌릴 수 있는 '빔 프로젝터'가 없어요". 버튼은 [날짜 바꾸기] [필터 초기화] |
| 오류 | `ErrorState` + [다시 시도]. 화면 전체가 아니라 **실패한 영역만** 교체. 입력값·필터는 유지 |
| 요청 전 날짜 충돌 | warning `Alert`: "9/23(수) – 9/24(목)은 다른 이웃이 빌리기로 했어요. 9/25(금)부터 가능해요." + 요청 버튼 비활성 |
| 승인 충돌 (409 등) | 해당 카드 안 danger `Alert`: "승인할 수 없어요 · 날짜가 겹쳐요" + "○○ 님의 대여(9/26 – 9/28)와 9/27 – 9/28이 겹쳐요" + [거절하고 이유 보내기] [겹치는 대여 보기] |
| 권한 없음 / 공개 중지 | "지금은 볼 수 없는 물건이에요" + [비슷한 물건 찾기] |

서버 오류와 충돌(비즈니스 규칙으로 거절됨)은 **다른 문구·다른 UI**로 보여 주세요. 충돌 응답에는 겹친 기간·상대 이름이 필요하니 API 설계 때 함께 맞춰 주세요.

### 목데이터 단계의 확인 방법

API 연동 전에는 상태를 띄울 계기가 없으므로 `useScreenState()`가 읽는 `?state=` 쿼리로 연다.
상태가 정의된 화면은 홈·물건 찾기·물건 상세·내 대여·채팅 5개다(`docs/ai/frontend.md` 상태 화면 표).
API 연동 시 이 쿼리를 실제 요청 상태로 바꾸고 화면 구조는 그대로 둔다.

---

## 10. 문구 규칙

- 해요체, 짧고 구체적으로. 상대는 "정우 님".
- 버튼은 동사로 끝나는 행동: 대여 요청, 승인, 거절, 전달 확인, 반납 확인, 요청 취소, 다시 시도. **"확인", "OK" 단독 금지.**
- 상태 이름은 §5 그대로. 동의어("대여됨", "진행 중") 쓰지 않기.
- 날짜 `9/26(토)`, 기간 `9/26(토) – 9/27(일) · 2일`, 시간 `오전 10:00`.
- 오류는 탓하지 않기: "불러오지 못했어요. 네트워크 연결을 확인한 뒤 다시 시도해 주세요."
- 이모지 쓰지 않아요.
- 무료 원칙 문구(푸터·안전 안내): "BILLIM의 대여는 모두 무료예요. 돈이나 보증금을 요구받으면 신고해 주세요."

---

## 11. 접근성

KWCAG 2.2 / WCAG 2.2 AA 기준.

- 텍스트 대비 4.5:1(24px 이상 3:1). 토큰 조합은 이미 맞춰 두었으니 **토큰끼리만 조합**하면 돼요.
- 컨트롤 테두리·아이콘·포커스 링 3:1. 포커스 링은 `outline: 2px solid var(--focus); outline-offset: 2px;` 이고 없애지 않아요.
- 터치 대상 44×44 이상.
- 아이콘만 있는 버튼은 `aria-label`. 알림 개수는 라벨에 포함("알림, 새 알림 2개").
- 상태는 색 + 아이콘 + 글자.
- 탭은 `role="tablist"/"tab"` + `aria-selected`, 필터 칩은 `aria-pressed`, 현재 메뉴는 `aria-current="page"`.
- 검색 결과 수, 채팅 새 메시지는 `aria-live="polite"`. 채팅 목록은 `role="log"`.
- 바텀시트·다이얼로그는 `role="dialog"` + 제목 연결 + 포커스 가두기 + Esc로 닫기.
- 달력 날짜 버튼 `aria-label="9월 23일 예약됨"`.
- `prefers-reduced-motion`이면 스켈레톤·스피너 애니메이션 끔.

---

## 12. 하지 말 것

- 토큰 밖의 색(특히 파랑·보라 그라데이션), 그림자, 글꼴 추가
- 카드에 그림자, 왼쪽 색 띠 카드
- 이모지를 아이콘 대신 사용
- 주소·호수 입력 필드, 사람·집 위치 지도 핀
- 별점·등급·퍼센트 신뢰 점수, "인증됨" 표시
- 결제·보증금·가격 UI
- 상태를 색으로만 표시, placeholder로 라벨 대신하기
- 원시 팔레트 토큰(`--gray-l1` 등)을 컴포넌트에서 직접 사용
- Array 글꼴을 로고 외 텍스트에 사용

---

**원본:** Claude 아티팩트 "BILLIM" 디자인 시스템(컴포넌트 미리보기·시안 포함). 토큰 값이 바뀌면 이 문서 §3과 `tokens.css`를 같은 PR에서 함께 고쳐 주세요.
