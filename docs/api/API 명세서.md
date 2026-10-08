# API 명세서

> 버전 : v0.1.2\
> 수정일 : 2026.10.07

## 0. 공통 규약

### 0.1 기본 규칙

| 항목 | 값 |
| --- | --- |
| Base URL | `/api/v1` |
| 표기 | JSON UTF-8, 요청 속성 camelCase. 선언하지 않은 요청 필드는 400 |
| ID | BIGINT ID는 JSON 문자열, version·sequence는 정수. ID 실제 상한은 signed BIGINT 최댓값이며 서버에서 검증 |
| 날짜 형식 | 일시는 UTC RFC 3339, 날짜는 KST `YYYY-MM-DD` |
| 선택 필드 | 응답 선택 필드는 값이 없으면 생략. null 미사용 |
| 응답 형식 | 성공은 응답 객체 자체 반환. 목록은 정의된 Page 형식, 실패는 ApiError. 204에는 본문 없음 |
| 페이지네이션 | 일반 목록 `page=0`, `size=20`, 최대 50. 안정적인 타이브레이커 id 사용. 변경 중 여러 페이지의 스냅샷 일관성은 미보장. 채팅·알림은 sequence/id 커서 |

### 0.2 인증 방식

- 쿠키 세션 인증. 개발은 같은 사이트 HTTPS 또는 로컬 프록시 구성을 권장하고 운영 쿠키는 Secure·HttpOnly·SameSite=Lax.
- 로그인 흐름: 로그인 전 `GET /auth/csrf` → `/auth/kakao` 최상위 이동 → callback → `/auth/complete` → `GET /auth/csrf`와 `/members/me`. 인증 코드와 제공자 토큰을 브라우저 저장소에 보관하지 않는다.
- POST/PUT/PATCH/DELETE는 `X-CSRF-TOKEN` 필수. OpenAPI의 cookie+csrf는 둘 다 필요한 AND 조건. OAuth callback은 state로 별도 검증한다.
- 로그인 세션은 제재돼도 기존 거래 정리를 위해 유효할 수 있다. 각 action은 계정 제재·소속·차단·당사자 정책을 별도로 확인한다.
- 익명 허용은 `/auth/csrf`, `/auth/kakao`, callback, `/policies/{policyKey}`만. ADMIN API는 전역 ADMIN 역할.
- 공개 조회가 허용되지 않는 타인 대상은 404, 이미 인지 가능한 대상의 명령 권한 부족은 403으로 처리한다.
- 응답 `allowedActions`·`access`는 UI 안내이며 서버 권한 검사를 대체하지 않는다. 로딩/토스트/모달/테마에는 별도 API가 없다.

### 0.3 동시성·멱등성

- expectedVersion 불일치 409. API에 적힌 expectedVersion은 해당 대상 또는 약속 슬롯의 version이다.
- 로그아웃을 제외한 POST에는 UUID `Idempotency-Key` 필수. 인증·조회 권한 재확인 후 같은 키/같은 요청은 최초 성공 결과·코드 재사용, 다른 내용은 409. 실패는 캐시하지 않고 24시간 보존.
- 채팅은 추가로 clientMessageId를 영구 고유 키로 사용한다. 변경 없는 PUT read/block, DELETE block은 반복에 안전하다.

### 0.4 오류 응답

```json
{
  "code": "RENTAL_DATE_CONFLICT",
  "message": "선택 기간에 이미 승인된 대여가 있습니다.",
  "requestId": "4429a68a-0270-4c19-9c5f-427b08aaac87",
  "conflictingRentalIds": [
    "501"
  ]
}
```

- conflictingRentalIds는 물건 소유자에게만 반환한다. 타인의 요청 생성 오류에는 거래 ID를 포함하지 않는다.
- currentVersion은 VERSION_CONFLICT 시 제공하며, 재조회 후 사용자가 의도를 확인해 재시도한다.
- 코드별 HTTP 상태·발생 조건 전체 목록: [에러 코드](에러%20코드.md)

### 0.5 상태값 요약

| 대상 | 필드 | 값 |
| --- | --- | --- |
| 대여 | status | `REQUESTED / APPROVED / REJECTED / CANCELED / ACTIVE / RETURNED / EXPIRED` |
| 대여 | overdue | 연체는 상태가 아니라 응답 boolean 파생값 |
| 약속 제안 | status | `PROPOSED / CONFIRMED / SUPERSEDED / WITHDRAWN` |
| 약속 슬롯 | kind | `PICKUP / RETURN` |
| 물건 | visibility | `PUBLIC / HIDDEN / DELETED` |
| 사진 | status | `TEMP / ATTACHED` |
| 신고 | status | `PENDING / RESOLVED` |
| 신고 조치 | action | `WARN / SUSPEND_7D / SUSPEND_30D / BAN / DISMISS` |
| 신고 사유 | reasonCode | `MONEY_REQUEST / ITEM_MISMATCH / NO_SHOW / ABUSE / FRAUD / OTHER` |
| 채팅 | access.reason | `NORMAL / BLOCKED / RESTRICTED / COMMUNITY_CHANGED / ITEM_UNAVAILABLE / EXISTING_RENTAL_ONLY` |

## 1. API 총괄표

| ID | 담당 | 단계 | Method | Path | 기능 |
| --- | --- | --- | --- | --- | --- |
| [A_001](#21-a_001-csrf-토큰과-로그인-전-세션-발급) | 이지은 | P0 | GET | /api/v1/auth/csrf | CSRF 토큰과 로그인 전 세션 발급 |
| [A_002](#22-a_002-카카오-로그인-시작) | 이지은 | P0 | GET | /api/v1/auth/kakao | 카카오 로그인 시작 |
| [A_003](#23-a_003-카카오-로그인-콜백) | 이지은 | P0 | GET | /api/v1/auth/kakao/callback | 카카오 로그인 콜백 |
| [A_004](#24-a_004-로그아웃) | 이지은 | P0 | POST | /api/v1/auth/logout | 로그아웃 |
| [A_005](#31-a_005-내-회원활성-소속-조회) | 이지은 | P0 | GET | /api/v1/members/me | 내 회원·활성 소속 조회 |
| [A_006](#32-a_006-내-표시-이름-수정) | 이지은 | P1 | PATCH | /api/v1/members/me | 내 표시 이름 수정 |
| [A_007](#33-a_007-이웃-공개-프로필) | 이지은 | P1 | GET | /api/v1/members/{memberId} | 이웃 공개 프로필 |
| [C_008](#34-c_008-회원-거래-신뢰-지표) | 박소빈 | P1 | GET | /api/v1/members/{memberId}/trust | 회원 거래 신뢰 지표 |
| [E_009](#41-e_009-동네-이름-검색) | 노현섭 | P0 | GET | /api/v1/communities | 동네 이름 검색 |
| [E_010](#42-e_010-동네-정보-조회) | 노현섭 | P0 | GET | /api/v1/communities/{communityId} | 동네 정보 조회 |
| [E_011](#43-e_011-위치-인증-및-활성-동네-선택) | 노현섭 | P0 | POST | /api/v1/communities/{communityId}/verifications | 위치 인증 및 활성 동네 선택 |
| [E_012](#44-e_012-공용-거래-장소-목록) | 노현섭 | P0 | GET | /api/v1/communities/{communityId}/places | 공용 거래 장소 목록 |
| [B_013](#51-b_013-공통-카테고리-목록) | 주정현 | P0 | GET | /api/v1/categories | 공통 카테고리 목록 |
| [B_014](#52-b_014-사진-업로드) | 주정현 | P0 | POST | /api/v1/media | 사진 업로드 |
| [B_015](#53-b_015-권한-있는-사진-읽기) | 주정현 | P0 | GET | /api/v1/media/{mediaId}/content | 권한 있는 사진 읽기 |
| [B_016](#54-b_016-미연결-업로드-삭제) | 주정현 | P0 | DELETE | /api/v1/media/{mediaId} | 미연결 업로드 삭제 |
| [B_017](#61-b_017-물건-검색-목록) | 주정현 | P0 | GET | /api/v1/items | 물건 검색 목록 |
| [B_018](#62-b_018-물건-검색-지도-집계) | 주정현 | P0 | GET | /api/v1/items/map | 물건 검색 지도 집계 |
| [B_019](#63-b_019-물건-등록) | 주정현 | P0 | POST | /api/v1/items | 물건 등록 |
| [B_020](#64-b_020-물건-상세) | 주정현 | P0 | GET | /api/v1/items/{itemId} | 물건 상세 |
| [B_021](#65-b_021-물건-정보와-사진-전체-수정) | 주정현 | P0 | PUT | /api/v1/items/{itemId} | 물건 정보와 사진 전체 수정 |
| [B_022](#66-b_022-공개공개-중지) | 주정현 | P0 | PATCH | /api/v1/items/{itemId}/visibility | 공개·공개 중지 |
| [B_023](#67-b_023-물건-논리-삭제) | 주정현 | P0 | DELETE | /api/v1/items/{itemId} | 물건 논리 삭제 |
| [B_024](#68-b_024-내-등록-물건-관리-목록) | 주정현 | P1 | GET | /api/v1/members/me/items | 내 등록 물건 관리 목록 |
| [B_025](#69-b_025-이웃-공개-물건-목록) | 주정현 | P1 | GET | /api/v1/members/{memberId}/items | 이웃 공개 물건 목록 |
| [C_026](#610-c_026-달력-예약-구간-조회) | 박소빈 | P0 | GET | /api/v1/items/{itemId}/availability | 달력 예약 구간 조회 |
| [B_027](#71-b_027-홈-화면-조립) | 주정현 | P0 | GET | /api/v1/home | 홈 화면 조립 |
| [C_028](#81-c_028-대여-요청-생성) | 박소빈 | P0 | POST | /api/v1/rentals | 대여 요청 생성 |
| [C_029](#82-c_029-내-대여-목록) | 박소빈 | P0 | GET | /api/v1/rentals | 내 대여 목록 |
| [C_030](#83-c_030-내-대여-상태별-건수) | 박소빈 | P0 | GET | /api/v1/rentals/summary | 내 대여 상태별 건수 |
| [C_031](#84-c_031-내-거래-상세와-상태-이력) | 박소빈 | P0 | GET | /api/v1/rentals/{rentalId} | 내 거래 상세와 상태 이력 |
| [C_032](#85-c_032-대여-승인) | 박소빈 | P0 | POST | /api/v1/rentals/{rentalId}/approve | 대여 승인 |
| [C_033](#86-c_033-대여-거절) | 박소빈 | P0 | POST | /api/v1/rentals/{rentalId}/reject | 대여 거절 |
| [C_034](#87-c_034-대여-취소) | 박소빈 | P0 | POST | /api/v1/rentals/{rentalId}/cancel | 대여 취소 |
| [C_035](#88-c_035-대여-전달-확인) | 박소빈 | P0 | POST | /api/v1/rentals/{rentalId}/handover | 대여 전달 확인 |
| [C_036](#89-c_036-대여-반납-확인) | 박소빈 | P0 | POST | /api/v1/rentals/{rentalId}/return | 대여 반납 확인 |
| [C_037](#91-c_037-전달반납-약속-조회) | 박소빈 | P1 | GET | /api/v1/rentals/{rentalId}/appointments | 전달·반납 약속 조회 |
| [C_038](#92-c_038-약속-제안변경-제안) | 박소빈 | P1 | POST | /api/v1/rentals/{rentalId}/appointments/{kind}/proposals | 약속 제안·변경 제안 |
| [C_039](#93-c_039-상대방-약속-수락) | 박소빈 | P1 | POST | /api/v1/appointments/{proposalId}/accept | 상대방 약속 수락 |
| [C_040](#94-c_040-내-미수락-약속-철회) | 박소빈 | P1 | POST | /api/v1/appointments/{proposalId}/withdraw | 내 미수락 약속 철회 |
| [D_041](#101-d_041-물건-문의-채팅방-열기) | 박선우 | P1 | POST | /api/v1/chat/rooms | 물건 문의 채팅방 열기 |
| [D_042](#102-d_042-채팅방-목록) | 박선우 | P1 | GET | /api/v1/chat/rooms | 채팅방 목록 |
| [D_043](#103-d_043-채팅방-정보연결-거래) | 박선우 | P1 | GET | /api/v1/chat/rooms/{roomId} | 채팅방 정보·연결 거래 |
| [D_044](#104-d_044-이전-대화새-메시지-조회) | 박선우 | P1 | GET | /api/v1/chat/rooms/{roomId}/messages | 이전 대화·새 메시지 조회 |
| [D_045](#105-d_045-텍스트-메시지-전송) | 박선우 | P1 | POST | /api/v1/chat/rooms/{roomId}/messages | 텍스트 메시지 전송 |
| [D_046](#106-d_046-채팅-읽은-위치-갱신) | 박선우 | P1 | PUT | /api/v1/chat/rooms/{roomId}/read | 채팅 읽은 위치 갱신 |
| [D_047](#107-d_047-전체-안-읽은-메시지-수) | 박선우 | P1 | GET | /api/v1/chat/unread-count | 전체 안 읽은 메시지 수 |
| [D_048](#111-d_048-내-알림-목록) | 박선우 | P0 | GET | /api/v1/notifications | 내 알림 목록 |
| [D_049](#112-d_049-안-읽은-알림-수) | 박선우 | P0 | GET | /api/v1/notifications/unread-count | 안 읽은 알림 수 |
| [D_050](#113-d_050-알림-개별-읽음) | 박선우 | P0 | PUT | /api/v1/notifications/{notificationId}/read | 알림 개별 읽음 |
| [D_051](#114-d_051-기준-id까지-모두-읽음) | 박선우 | P0 | PUT | /api/v1/notifications/read-all | 기준 ID까지 모두 읽음 |
| [E_052](#121-e_052-내-차단-목록) | 노현섭 | P1 | GET | /api/v1/members/me/blocks | 내 차단 목록 |
| [E_053](#122-e_053-이웃-차단) | 노현섭 | P1 | PUT | /api/v1/members/me/blocks/{memberId} | 이웃 차단 |
| [E_054](#123-e_054-내-차단-해제) | 노현섭 | P1 | DELETE | /api/v1/members/me/blocks/{memberId} | 내 차단 해제 |
| [E_055](#124-e_055-신고-접수) | 노현섭 | P1 | POST | /api/v1/reports | 신고 접수 |
| [E_056](#125-e_056-내-신고-목록) | 노현섭 | P1 | GET | /api/v1/members/me/reports | 내 신고 목록 |
| [E_057](#126-e_057-내-신고-결과-상세) | 노현섭 | P1 | GET | /api/v1/reports/{reportId} | 내 신고 결과 상세 |
| [E_058](#131-e_058-운영-신고-목록) | 노현섭 | P1 | GET | /api/v1/admin/reports | 운영 신고 목록 |
| [E_059](#132-e_059-신고-증거와-처리-상세) | 노현섭 | P1 | GET | /api/v1/admin/reports/{reportId} | 신고 증거와 처리 상세 |
| [E_060](#133-e_060-신고-처리제재-결정) | 노현섭 | P1 | POST | /api/v1/admin/reports/{reportId}/resolve | 신고 처리·제재 결정 |
| [E_061](#134-e_061-제재-이력-조회) | 노현섭 | P1 | GET | /api/v1/admin/sanctions | 제재 이력 조회 |
| [E_062](#135-e_062-제재-해제) | 노현섭 | P1 | POST | /api/v1/admin/sanctions/{sanctionId}/revoke | 제재 해제 |
| [E_063](#141-e_063-동네-공지-목록) | 노현섭 | P2 | GET | /api/v1/communities/{communityId}/notices | 동네 공지 목록 |
| [E_064](#142-e_064-공지-상세) | 노현섭 | P2 | GET | /api/v1/notices/{noticeId} | 공지 상세 |
| [E_065](#143-e_065-관리자-공지-목록) | 노현섭 | P2 | GET | /api/v1/admin/notices | 관리자 공지 목록 |
| [E_066](#144-e_066-공지-작성) | 노현섭 | P2 | POST | /api/v1/admin/notices | 공지 작성 |
| [E_067](#145-e_067-공지-수정고정) | 노현섭 | P2 | PUT | /api/v1/admin/notices/{noticeId} | 공지 수정·고정 |
| [E_068](#146-e_068-공지-삭제) | 노현섭 | P2 | DELETE | /api/v1/admin/notices/{noticeId} | 공지 삭제 |
| [E_069](#136-e_069-동네-운영-통계) | 노현섭 | P2 | GET | /api/v1/admin/statistics | 동네 운영 통계 |
| [E_070](#147-e_070-정책안내-정적-문서) | 노현섭 | P1 | GET | /api/v1/policies/{policyKey} | 정책·안내 정적 문서 |
| [E_071](#45-e_071-현재-위치로-주변-동네-찾기) | 노현섭 | P0 | POST | /api/v1/communities/nearby | 현재 위치로 주변 동네 찾기 |

## 2. 인증 (auth)

> `auth_sessions`, `oauth_login_attempts`, `members`, `social_accounts`

### 2.1 A_001 CSRF 토큰과 로그인 전 세션 발급

- `GET /api/v1/auth/csrf` · 이지은 · P0
- 설명: 익명도 사용. BILLIM_SESSION 쿠키 발급 또는 기존 세션의 CSRF 토큰 반환. Cache-Control: no-store. 로그인 후 다시 조회.
- 요청 본문: 없음
- 성공 응답: 200 CsrfToken
- 관련 테이블: auth_sessions
- 목업 연결: login

### 2.2 A_002 카카오 로그인 시작

- `GET /api/v1/auth/kakao` · 이지은 · P0
- 설명: 브라우저 최상위 이동. 로그인 전 세션과 state를 만들고 허용된 카카오 인증 주소로 302 이동. 임의 returnUrl 입력 없음.
- 요청 본문: 없음
- 성공 응답: 302 리다이렉트
- 관련 테이블: auth_sessions, oauth_login_attempts
- 목업 연결: login

### 2.3 A_003 카카오 로그인 콜백

- `GET /api/v1/auth/kakao/callback` · 이지은 · P0
- 설명: code 또는 error를 받는다. state+세션 확인·1회 소비 후 서버 토큰 교환/사용자 식별. 성공 시 세션 회전, 앱의 고정 /auth/complete 경로로 302. 사용자 취소는 /login?error=OAUTH_CANCELED. state 불일치는 400. 제한 계정도 로그인 후 기존 거래 정리만 허용.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| state | Y | string | 로그인 시 발급한 state |
| code | N | string | 성공 콜백 인가 코드 |
| error | N | string | 인가 거부 등 제공자 오류 |

- 요청 본문: 없음
- 성공 응답: 302 리다이렉트
- 관련 테이블: members, social_accounts, auth_sessions, oauth_login_attempts
- 목업 연결: login

### 2.4 A_004 로그아웃

- `POST /api/v1/auth/logout` · 이지은 · P0
- 설명: 세션 폐기와 쿠키 만료. 로그인된 세션의 CSRF 필요.
- 요청 본문: 없음
- 성공 응답: 204 본문 없음
- 관련 테이블: auth_sessions
- 목업 연결: profile

## 3. 회원 (members)

> `members`, `community_memberships`, `sanctions`, `blocks`, `rentals`

### 3.1 A_005 내 회원·활성 소속 조회

- `GET /api/v1/members/me` · 이지은 · P0
- 설명: 유효 로그인만 필요. 미인증/제재 상태도 자신의 onboarding·제한 상태를 조회할 수 있다.
- 요청 본문: 없음
- 성공 응답: 200 MyProfile
- 관련 테이블: members, community_memberships, sanctions
- 목업 연결: profile, joindone

### 3.2 A_006 내 표시 이름 수정

- `PATCH /api/v1/members/me` · 이지은 · P1
- 설명: 본인 ACTIVE 회원. 2~30자, version 불일치 409. 사진 편집은 범위 밖.
- 요청 본문: UpdateProfile
- 성공 응답: 200 MyProfile
- 관련 테이블: members
- 목업 연결: profile

### 3.3 A_007 이웃 공개 프로필

- `GET /api/v1/members/{memberId}` · 이지은 · P1
- 설명: 동일 유효 활성 동네의 회원 또는 기존 거래 당사자. 기존 거래 없이 차단 관계면 404. 소셜 식별자·개인 연락처 미노출.
- 요청 본문: 없음
- 성공 응답: 200 PublicProfile
- 관련 테이블: members, community_memberships, blocks, rentals
- 목업 연결: profileOther

### 3.4 C_008 회원 거래 신뢰 지표

- `GET /api/v1/members/{memberId}/trust` · 박소빈 · P1
- 설명: 공개 프로필과 동일 권한. 완료·빌려준 횟수와 확정 반납 약속의 서버 확인 기준 준수 분자/분모. 본인 조회 가능.
- 요청 본문: 없음
- 성공 응답: 200 TrustMetrics
- 관련 테이블: rentals
- 목업 연결: profile, profileOther, detail

## 4. 커뮤니티·장소 (communities)

> `communities`, `community_memberships`, `members`, `community_places`

### 4.1 E_009 동네 이름 검색

- `GET /api/v1/communities` · 노현섭 · P0
- 설명: 로그인 필요, 미인증 허용. 활성 커뮤니티 이름순,id순. 위치 없이 검색 가능.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| keyword | N | string | 이름 부분 검색; 생략하면 활성 동네 목록 |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 CommunityPage
- 관련 테이블: communities, community_memberships
- 목업 연결: join

### 4.2 E_010 동네 정보 조회

- `GET /api/v1/communities/{communityId}` · 노현섭 · P0
- 설명: 로그인 필요, 미인증 허용. 비활성 동네는 404.
- 요청 본문: 없음
- 성공 응답: 200 Community
- 관련 테이블: communities
- 목업 연결: verify

### 4.3 E_011 위치 인증 및 활성 동네 선택

- `POST /api/v1/communities/{communityId}/verifications` · 노현섭 · P0
- 설명: 현재 위치는 이 요청에서만 사용. 범위 밖 403 LOCATION_OUTSIDE, 정확도 초과 422 LOCATION_INACCURATE. 성공 시 이전 소속 LEFT, 선택 소속 ACTIVE, members.active_community_id 갱신. 좌표는 DB·로그·멱등 응답에 저장하지 않는다. 최초/재인증 모두 200.
- 요청 본문: VerifyLocationRequest
- 성공 응답: 200 Membership
- 관련 테이블: members, communities, community_memberships
- 목업 연결: verify, joindone

### 4.4 E_012 공용 거래 장소 목록

- `GET /api/v1/communities/{communityId}/places` · 노현섭 · P0
- 설명: 유효 활성 동네 또는 해당 동네의 기존 거래 당사자. 현재 active 장소만 이름순,id순 반환.
- 요청 본문: 없음
- 성공 응답: 200 PlaceList
- 관련 테이블: community_places, community_memberships
- 목업 연결: register, chat

### 4.5 E_071 현재 위치로 주변 동네 찾기

- `POST /api/v1/communities/nearby` · 노현섭 · P0
- 설명: 로그인 필요·인증 전 허용. 요청 좌표와 활성 동네 중심의 거리를 계산해 가까운 순,id순으로 최대 20개 반환. 추가 후보가 있으면 truncated=true; 이름 검색으로 보완. 좌표와 정확도는 일시 사용하고 저장/로그 금지. 이 조회 자체는 소속 인증이 아니다.
- 요청 본문: VerifyLocationRequest
- 성공 응답: 200 NearbyCommunities
- 관련 테이블: communities
- 목업 연결: join

## 5. 카테고리·사진 (categories·media)

> `categories`, `media_files`, `item_images`, `items`, `rentals`

### 5.1 B_013 공통 카테고리 목록

- `GET /api/v1/categories` · 주정현 · P0
- 설명: 로그인 회원. active 분류를 sortOrder,id순 반환.
- 요청 본문: 없음
- 성공 응답: 200 CategoryList
- 관련 테이블: categories
- 목업 연결: home, register, search

### 5.2 B_014 사진 업로드

- `POST /api/v1/media` · 주정현 · P0
- 설명: multipart/form-data file 1개. 유효 인증·제한 없음. 10 MiB 이하 JPEG/PNG/WebP; 최대 40MP 디코딩 한도 제안. 실제 이미지 검사 후 메타데이터 제거. TEMP 만료 24시간.
- 요청 본문: Upload
- 성공 응답: 201 MediaFile
- 관련 테이블: media_files
- 목업 연결: register

### 5.3 B_015 권한 있는 사진 읽기

- `GET /api/v1/media/{mediaId}/content` · 주정현 · P0
- 설명: TEMP는 업로더만. ATTACHED는 물건 조회 권한 또는 해당 물건의 기존 거래 당사자. 비공개/차단 권한을 이미지 URL에서도 적용. private cache, 외부 공개 정적 URL 금지.
- 요청 본문: 없음
- 성공 응답: 200 이미지 바이너리
- 관련 테이블: media_files, item_images, items, rentals
- 목업 연결: detail, register

### 5.4 B_016 미연결 업로드 삭제

- `DELETE /api/v1/media/{mediaId}` · 주정현 · P0
- 설명: 업로더 자신의 TEMP만 삭제. ATTACHED는 409 MEDIA_IN_USE. 파일 정리는 재시도 가능하게 DB 상태에 따라 수행.
- 요청 본문: 없음
- 성공 응답: 204 본문 없음
- 관련 테이블: media_files
- 목업 연결: register

## 6. 물건 (items)

> `items`, `categories`, `item_images`, `rentals`, `blocks`, `community_places`, `media_files`, `members`

### 6.1 B_017 물건 검색 목록

- `GET /api/v1/items` · 주정현 · P0
- 설명: 유효 활성 동네로 서버가 범위 제한. PUBLIC·차단/제재 없음만. LATEST=createdAt DESC,id DESC; NEAREST=distance ASC,id DESC. 날짜 두 개 동시 입력, C의 겹침·연체 규칙 사용. distanceBasis=COMMUNITY_CENTER.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| keyword | N | string | 제목 부분 검색 |
| categoryId | N | string | 분류 |
| startDate | N | string | endDate와 함께 지정; KST 오늘 이상, 최대 30일 |
| endDate | N | string | startDate와 함께 지정; 양끝 포함 |
| placeId | N | string | 선택 공용 장소 |
| sort | N | string LATEST/NEAREST | 기본 LATEST; NEAREST는 동네 중심 거리 |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 ItemPage
- 관련 테이블: items, categories, item_images, rentals, blocks
- 목업 연결: search, home

### 6.2 B_018 물건 검색 지도 집계

- `GET /api/v1/items/map` · 주정현 · P0
- 설명: items와 같은 노출/기간 규칙. 장소별 개수, 최대 200개 장소; 초과 시 truncated=true. 지도 범위는 선택 bbox 4개를 함께 전달. 화면 이동에 따른 지역 확대 권한 없음.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| keyword | N | string | 제목 부분 검색 |
| categoryId | N | string | 분류 |
| startDate | N | string | endDate와 함께 지정; KST 오늘 이상, 최대 30일 |
| endDate | N | string | startDate와 함께 지정; 양끝 포함 |
| minLat | N | number | 지도 남쪽 경계 |
| maxLat | N | number | 지도 북쪽 경계 |
| minLng | N | number | 서쪽 경계 |
| maxLng | N | number | 동쪽 경계 |

- 요청 본문: 없음
- 성공 응답: 200 MapResults
- 관련 테이블: items, community_places, rentals, blocks
- 목업 연결: search

### 6.3 B_019 물건 등록

- `POST /api/v1/items` · 주정현 · P0
- 설명: 소유자·커뮤니티는 서버 결정. 날짜 역전 불가. 자기 TEMP 사진 1~5개를 순서대로 연결, 다른 동네 장소 금지. 초기 PUBLIC.
- 요청 본문: CreateItem
- 성공 응답: 201 ItemDetail
- 관련 테이블: items, item_images, media_files
- 목업 연결: register

### 6.4 B_020 물건 상세

- `GET /api/v1/items/{itemId}` · 주정현 · P0
- 설명: 본인은 HIDDEN도 조회. 타인은 같은 유효 동네 PUBLIC·차단 없음. DELETED 또는 접근 불가 404. 과거 거래는 rental detail에서 조회.
- 요청 본문: 없음
- 성공 응답: 200 ItemDetail
- 관련 테이블: items, item_images, members, community_places, blocks
- 목업 연결: detail

### 6.5 B_021 물건 정보와 사진 전체 수정

- `PUT /api/v1/items/{itemId}` · 주정현 · P0
- 설명: 소유자·유효 해당 동네만. 물건 행 잠금. 가능 기간은 REQUESTED/APPROVED/ACTIVE를 모두 포함해야 함. 진행 거래 중 사진 제거 금지. description 생략은 제거. imageIds는 보유 연결 사진과 자신의 TEMP만 허용.
- 요청 본문: UpdateItem
- 성공 응답: 200 ItemDetail
- 관련 테이블: items, rentals, media_files, item_images
- 목업 연결: register

### 6.6 B_022 공개·공개 중지

- `PATCH /api/v1/items/{itemId}/visibility` · 주정현 · P0
- 설명: 소유자만. HIDDEN 전환은 인증 만료/소속 변경에도 정리 목적으로 허용; PUBLIC 복원은 해당 동네 유효 소속·제한 없음 필요. 승인/진행 거래 유지.
- 요청 본문: VisibilityChange
- 성공 응답: 200 ItemDetail
- 관련 테이블: items
- 목업 연결: profile, detail

### 6.7 B_023 물건 논리 삭제

- `DELETE /api/v1/items/{itemId}` · 주정현 · P0
- 설명: 소유자만. REQUESTED/APPROVED/ACTIVE가 있으면 409 ITEM_HAS_OPEN_RENTALS. 기록/사진은 즉시 물리 삭제하지 않는다.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| expectedVersion | Y | integer | 마지막 조회한 version |

- 요청 본문: 없음
- 성공 응답: 204 본문 없음
- 관련 테이블: items, rentals
- 목업 연결: profile

### 6.8 B_024 내 등록 물건 관리 목록

- `GET /api/v1/members/me/items` · 주정현 · P1
- 설명: 본인 물건의 PUBLIC/HIDDEN 표시. 소속 변경 전 물건도 조회. DELETED 제외. createdAt DESC,id DESC.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 ItemPage
- 관련 테이블: items
- 목업 연결: profile

### 6.9 B_025 이웃 공개 물건 목록

- `GET /api/v1/members/{memberId}/items` · 주정현 · P1
- 설명: 공개 프로필 및 물건 검색 권한 적용. 현재 동네 PUBLIC만.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 ItemPage
- 관련 테이블: items, blocks
- 목업 연결: profileOther

### 6.10 C_026 달력 예약 구간 조회

- `GET /api/v1/items/{itemId}/availability` · 박소빈 · P0
- 설명: 물건 상세와 같은 권한. from/to 필수, 최대 93일. 점유 기간을 요청 범위로 자르고 병합해 반환하며 회원/거래 ID는 노출하지 않음. 선택 start/end는 함께 입력하고 최대 30일.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| from | Y | string | 달력 시작일 |
| to | Y | string | 달력 끝일; 최대 93일 |
| startDate | N | string | endDate와 함께 지정; KST 오늘 이상, 최대 30일 |
| endDate | N | string | startDate와 함께 지정; 양끝 포함 |

- 요청 본문: 없음
- 성공 응답: 200 Availability
- 관련 테이블: items, rentals
- 목업 연결: detail, request

## 7. 홈 (home)

> `items`, `community_memberships`, `rentals`, `notifications`, `chat_participants`

### 7.1 B_027 홈 화면 조립

- `GET /api/v1/home` · 주정현 · P0
- 설명: 유효 활성 동네. 최신 물건 6개, 내 대여 요약, 카테고리·회원 수·알림 수. 조회 원천은 각 도메인. P1 전 unreadChatCount=0.
- 요청 본문: 없음
- 성공 응답: 200 Home
- 관련 테이블: items, community_memberships, rentals, notifications, chat_participants
- 목업 연결: home

## 8. 대여 (rentals)

> `rentals`, `rental_status_histories`, `outbox_events`, `appointment_slots`

### 8.1 C_028 대여 요청 생성

- `POST /api/v1/rentals` · 박소빈 · P0
- 설명: 유효 소속·본인 물건 아님·차단 없음. 날짜 양끝 포함, 최대 30일. 물건 잠금 아래 가능 기간/연체/승인 점유 재검사. 동일인의 겹치는 대기 요청 거부, 다른 사람의 대기 요청 겹침 허용.
- 요청 본문: CreateRental
- 성공 응답: 201 RentalDetail
- 관련 테이블: rentals, rental_status_histories, outbox_events
- 목업 연결: request

### 8.2 C_029 내 대여 목록

- `GET /api/v1/rentals` · 박소빈 · P0
- 설명: role 필수 BORROWER/OWNER. 생성 시각 DESC,id DESC. 소속 변경·인증 만료·차단 후에도 본인 거래 조회. overdueOnly는 ACTIVE 필터와 병용 또는 상태 생략.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| role | Y | string BORROWER/OWNER | 내 역할 |
| status | N | string REQUESTED/APPROVED/REJECTED/CANCELED/ACTIVE/RETURNED/EXPIRED | 상태 |
| overdueOnly | N | boolean | 연체만; 기본 false |
| itemId | N | string | 내 특정 물건/거래 |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 RentalPage
- 관련 테이블: rentals
- 목업 연결: rentals

### 8.3 C_030 내 대여 상태별 건수

- `GET /api/v1/rentals/summary` · 박소빈 · P0
- 설명: 차단/소속 변경과 무관한 본인 거래. active는 연체 포함, overdue는 그 부분집합. nextRentals는 예정 전달/반납 3건.
- 요청 본문: 없음
- 성공 응답: 200 MyRentalSummary
- 관련 테이블: rentals
- 목업 연결: rentals, home

### 8.4 C_031 내 거래 상세와 상태 이력

- `GET /api/v1/rentals/{rentalId}` · 박소빈 · P0
- 설명: 소유자/요청자만. 비공개 물건이라도 거래 당시 제목·장소 스냅샷 유지. 무관한 회원은 404. P1 전 appointments는 빈 배열.
- 요청 본문: 없음
- 성공 응답: 200 RentalDetail
- 관련 테이블: rentals, rental_status_histories, appointment_slots
- 목업 연결: rentals, chat

### 8.5 C_032 대여 승인

- `POST /api/v1/rentals/{rentalId}/approve` · 박소빈 · P0
- 설명: 소유자만 REQUESTED→APPROVED. 유효 소속·제재·차단·현재 날짜·가능 기간 재검사. 물건 잠금으로 겹침 방지. 충돌 시 409 RENTAL_DATE_CONFLICT; 소유자에게만 충돌 거래 ID 제공.
- 요청 본문: VersionCommand
- 성공 응답: 200 RentalDetail
- 관련 테이블: rentals, rental_status_histories, outbox_events
- 목업 연결: rentals

### 8.6 C_033 대여 거절

- `POST /api/v1/rentals/{rentalId}/reject` · 박소빈 · P0
- 설명: 소유자만 REQUESTED→REJECTED. reason 필수, 공백만 입력 불가, 최대 500자. 제한 계정도 거래 정리 허용.
- 요청 본문: ReasonCommand
- 성공 응답: 200 RentalDetail
- 관련 테이블: rentals, rental_status_histories, outbox_events
- 목업 연결: rentals

### 8.7 C_034 대여 취소

- `POST /api/v1/rentals/{rentalId}/cancel` · 박소빈 · P0
- 설명: REQUESTED는 요청자만, APPROVED는 양측 가능. 모든 취소에 reason 필수, 공백만 입력 불가, 최대 500자. ACTIVE/종료 상태 거부.
- 요청 본문: CancelCommand
- 성공 응답: 200 RentalDetail
- 관련 테이블: rentals, rental_status_histories, outbox_events
- 목업 연결: rentals

### 8.8 C_035 대여 전달 확인

- `POST /api/v1/rentals/{rentalId}/handover` · 박소빈 · P0
- 설명: 소유자만 APPROVED→ACTIVE. KST 오늘이 약정 기간 안, 같은 물건에 다른 ACTIVE 없음. 기존 거래 정리 권한 적용.
- 요청 본문: VersionCommand
- 성공 응답: 200 RentalDetail
- 관련 테이블: rentals, rental_status_histories, outbox_events
- 목업 연결: rentals

### 8.9 C_036 대여 반납 확인

- `POST /api/v1/rentals/{rentalId}/return` · 박소빈 · P0
- 설명: 소유자만 ACTIVE→RETURNED. 서버 확인 시각·반납 약속 스냅샷 저장. 반복 실행의 version/state 검사 및 멱등 재응답.
- 요청 본문: VersionCommand
- 성공 응답: 200 RentalDetail
- 관련 테이블: rentals, rental_status_histories, outbox_events
- 목업 연결: rentals

## 9. 약속 (appointments)

> `appointment_slots`, `appointment_proposals`, `outbox_events`

### 9.1 C_037 전달·반납 약속 조회

- `GET /api/v1/rentals/{rentalId}/appointments` · 박소빈 · P1
- 설명: 거래 당사자만. APPROVED 진입 시 PICKUP/RETURN 두 슬롯 생성. 이전 P0 거래는 마이그레이션에서 생성. 아직 승인 전이면 빈 목록.
- 요청 본문: 없음
- 성공 응답: 200 AppointmentSlots
- 관련 테이블: appointment_slots, appointment_proposals
- 목업 연결: chat, rentals

### 9.2 C_038 약속 제안·변경 제안

- `POST /api/v1/rentals/{rentalId}/appointments/{kind}/proposals` · 박소빈 · P1
- 설명: kind=PICKUP/RETURN. 거래 당사자, 슬롯 version 필수. 미래이며 KST 날짜가 거래 시작/끝 날짜와 같음. PICKUP은 APPROVED, RETURN은 APPROVED/ACTIVE. 이전 PROPOSED 교체, CONFIRMED는 수락 전 유지.
- 요청 본문: ProposeAppointment
- 성공 응답: 201 AppointmentSlot
- 관련 테이블: appointment_slots, appointment_proposals, outbox_events
- 목업 연결: chat

### 9.3 C_039 상대방 약속 수락

- `POST /api/v1/appointments/{proposalId}/accept` · 박소빈 · P1
- 설명: 거래 상대방만, 자기 제안 수락 불가. expectedVersion은 proposal이 아닌 슬롯 version. 상태/시각/장소·대여 상태 재검사. 새 확정 시 이전 확정은 SUPERSEDED.
- 요청 본문: VersionCommand
- 성공 응답: 200 AppointmentSlot
- 관련 테이블: appointment_slots, appointment_proposals, outbox_events
- 목업 연결: chat

### 9.4 C_040 내 미수락 약속 철회

- `POST /api/v1/appointments/{proposalId}/withdraw` · 박소빈 · P1
- 설명: 제안자만 PROPOSED→WITHDRAWN. 슬롯 version 비교. 기존 확정 약속 유지.
- 요청 본문: VersionCommand
- 성공 응답: 200 AppointmentSlot
- 관련 테이블: appointment_slots, appointment_proposals, outbox_events
- 목업 연결: chat

## 10. 채팅 (chat)

> `chat_rooms`, `chat_participants`, `rentals`, `messages`, `blocks`, `sanctions`

### 10.1 D_041 물건 문의 채팅방 열기

- `POST /api/v1/chat/rooms` · 박선우 · P1
- 설명: 물건 공개 조회 권한·본인 물건 아님·차단 없음. item+requester UNIQUE로 방 재사용. 동일 당사자 기존 대여 연결. 생성/재사용 모두 200. 기존 거래 당사자의 기존 방 접근은 상세 API 사용.
- 요청 본문: CreateRoom
- 성공 응답: 200 ChatRoom
- 관련 테이블: chat_rooms, chat_participants, rentals
- 목업 연결: detail, chat

### 10.2 D_042 채팅방 목록

- `GET /api/v1/chat/rooms` · 박선우 · P1
- 설명: 본인 참여 방만. 일반 문의방도 ALL에 포함. BORROWER/OWNER는 연결 거래 존재 기준. lastMessageAt DESC(없으면 createdAt),id DESC.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| filter | N | string ALL/BORROWER/OWNER/UNREAD | 기본 ALL |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 ChatRoomPage
- 관련 테이블: chat_rooms, messages, chat_participants, rentals
- 목업 연결: chat

### 10.3 D_043 채팅방 정보·연결 거래

- `GET /api/v1/chat/rooms/{roomId}` · 박선우 · P1
- 설명: 참여자만. 차단 후 읽기는 가능하고 access에 전송 제한과 기존 거래 예외 반환.
- 요청 본문: 없음
- 성공 응답: 200 ChatRoom
- 관련 테이블: chat_rooms, rentals, blocks, sanctions
- 목업 연결: chat

### 10.4 D_044 이전 대화·새 메시지 조회

- `GET /api/v1/chat/rooms/{roomId}/messages` · 박선우 · P1
- 설명: 참여자만. 기본 최신 DESC. afterSequence 사용 시 ASC, beforeSequence 사용 시 DESC; 상호 배타. hasMore이면 같은 방향의 next 커서로 계속 읽음.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| afterSequence | N | integer | 이 값 초과 새 메시지 |
| beforeSequence | N | integer | 이 값 미만 이전 메시지 |
| limit | N | integer | 기본 50; 최소 1, 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 MessagePage
- 관련 테이블: messages, chat_participants
- 목업 연결: chat

### 10.5 D_045 텍스트 메시지 전송

- `POST /api/v1/chat/rooms/{roomId}/messages` · 박선우 · P1
- 설명: 참여자·전송 가능 검사. 차단/제재 예외에는 APPROVED/ACTIVE 거래 rentalId 필수. 방 잠금으로 sequence 할당. clientMessageId 동일+같은 내용이면 기존 메시지, 다른 내용 409 MESSAGE_KEY_REUSED. 시스템 메시지를 클라이언트가 생성할 수 없음.
- 요청 본문: SendMessage
- 성공 응답: 201 Message
- 관련 테이블: messages, chat_rooms
- 목업 연결: chat

### 10.6 D_046 채팅 읽은 위치 갱신

- `PUT /api/v1/chat/rooms/{roomId}/read` · 박선우 · P1
- 설명: 본인 읽음 위치만. 해당 방에 존재하는 sequence 또는 0. 현재보다 작은 값은 현재 값으로 성공 반환.
- 요청 본문: ReadPosition
- 성공 응답: 200 ReadPosition
- 관련 테이블: chat_participants, messages
- 목업 연결: chat

### 10.7 D_047 전체 안 읽은 메시지 수

- `GET /api/v1/chat/unread-count` · 박선우 · P1
- 설명: 참여 방의 상대방 TEXT 중 lastReadSequence 초과 합계; SYSTEM 제외.
- 요청 본문: 없음
- 성공 응답: 200 UnreadCount
- 관련 테이블: chat_participants, messages
- 목업 연결: home, chat

## 11. 알림 (notifications)

> `notifications`

### 11.1 D_048 내 알림 목록

- `GET /api/v1/notifications` · 박선우 · P0
- 설명: 본인 수신만, id DESC. cursor는 exclusive id. read=true/false 생략하면 전체.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| cursor | N | string | 직전 페이지 nextCursor |
| limit | N | integer | 기본 20 |
| read | N | boolean | 읽음 여부 필터 |

- 요청 본문: 없음
- 성공 응답: 200 NotificationPage
- 관련 테이블: notifications
- 목업 연결: notifications

### 11.2 D_049 안 읽은 알림 수

- `GET /api/v1/notifications/unread-count` · 박선우 · P0
- 설명: 본인 readAt 없는 수.
- 요청 본문: 없음
- 성공 응답: 200 UnreadCount
- 관련 테이블: notifications
- 목업 연결: home, notifications

### 11.3 D_050 알림 개별 읽음

- `PUT /api/v1/notifications/{notificationId}/read` · 박선우 · P0
- 설명: 본인만. 이미 읽었으면 최초 readAt 유지.
- 요청 본문: 없음
- 성공 응답: 200 Notification
- 관련 테이블: notifications
- 목업 연결: notifications

### 11.4 D_051 기준 ID까지 모두 읽음

- `PUT /api/v1/notifications/read-all` · 박선우 · P0
- 설명: 본인 알림 중 id<=throughId만 처리. 방금 수신한 더 큰 ID의 알림은 읽음 처리하지 않음. throughId는 목록에서 관측한 본인 ID.
- 요청 본문: ReadAllNotifications
- 성공 응답: 204 본문 없음
- 관련 테이블: notifications
- 목업 연결: notifications

## 12. 차단·신고 (blocks·reports)

> `blocks`, `members`, `reports`, `report_actions`

### 12.1 E_052 내 차단 목록

- `GET /api/v1/members/me/blocks` · 노현섭 · P1
- 설명: 내가 차단한 방향만. createdAt DESC,id DESC.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 BlockPage
- 관련 테이블: blocks, members
- 목업 연결: blocked

### 12.2 E_053 이웃 차단

- `PUT /api/v1/members/me/blocks/{memberId}` · 노현섭 · P1
- 설명: 본인 제외, 동일 동네 또는 기존 거래 상대. 양측 회원 행 ID순 잠금. 이미 차단했으면 성공. 기존 APPROVED/ACTIVE 정리 예외는 유지.
- 요청 본문: 없음
- 성공 응답: 204 본문 없음
- 관련 테이블: blocks, members
- 목업 연결: profileOther, chat

### 12.3 E_054 내 차단 해제

- `DELETE /api/v1/members/me/blocks/{memberId}` · 노현섭 · P1
- 설명: 내가 만든 방향만 제거. 상대방도 차단했다면 상호 노출/일반 채팅은 계속 제한. 없는 관계도 성공.
- 요청 본문: 없음
- 성공 응답: 204 본문 없음
- 관련 테이블: blocks
- 목업 연결: blocked

### 12.4 E_055 신고 접수

- `POST /api/v1/reports` · 노현섭 · P1
- 설명: 신고 대상 접근 권한 필요. MEMBER/ITEM/MESSAGE targetId를 서버 해석하고 targetMember 결정. OTHER는 detail 필수. 자기 신고·동일 미처리 대상/사유 중복 금지.
- 요청 본문: CreateReport
- 성공 응답: 201 Report
- 관련 테이블: reports
- 목업 연결: detail, profileOther, chat

### 12.5 E_056 내 신고 목록

- `GET /api/v1/members/me/reports` · 노현섭 · P1
- 설명: 내 접수 내역만. internalNote/신고자 정보는 응답 제외.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 ReportPage
- 관련 테이블: reports, report_actions
- 목업 연결: reports

### 12.6 E_057 내 신고 결과 상세

- `GET /api/v1/reports/{reportId}` · 노현섭 · P1
- 설명: 신고자만. 제재 대상에게는 회원 제한 상태/별도 결과 알림의 최소 사유만 제공, 신고자 정보 비공개.
- 요청 본문: 없음
- 성공 응답: 200 Report
- 관련 테이블: reports, report_actions
- 목업 연결: reports

## 13. 관리자 (admin)

> `reports`, `report_actions`, `messages`, `items`, `sanctions`, `outbox_events`, `members`, `rentals`, `community_memberships`

### 13.1 E_058 운영 신고 목록

- `GET /api/v1/admin/reports` · 노현섭 · P1
- 설명: ADMIN만. createdAt DESC,id DESC.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| status | N | string PENDING/RESOLVED | 상태 |
| communityId | N | string | 동네 필터 |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 AdminReportPage
- 관련 테이블: reports, report_actions
- 목업 연결: adminReports

### 13.2 E_059 신고 증거와 처리 상세

- `GET /api/v1/admin/reports/{reportId}` · 노현섭 · P1
- 설명: ADMIN만. 신고 대상의 표시 정보·신고 당시 보존된 거래/메시지 이력을 제한적으로 조회. internalNote는 이 관리자 API에만 노출.
- 요청 본문: 없음
- 성공 응답: 200 AdminReport
- 관련 테이블: reports, report_actions, messages, items
- 목업 연결: adminReports

### 13.3 E_060 신고 처리·제재 결정

- `POST /api/v1/admin/reports/{reportId}/resolve` · 노현섭 · P1
- 설명: ADMIN만. 대상 회원→report 잠금, PENDING/version 재검사. 처리·sanction·알림 outbox를 한 트랜잭션. WARN/DISMISS는 제재 행 없음. 관리자 자신에 대한 제재와 ADMIN 대상 제한은 금지.
- 요청 본문: ResolveReport
- 성공 응답: 200 AdminReport
- 관련 테이블: reports, report_actions, sanctions, outbox_events
- 목업 연결: adminReports

### 13.4 E_061 제재 이력 조회

- `GET /api/v1/admin/sanctions` · 노현섭 · P1
- 설명: ADMIN만. 만료·해제 포함 전체 이력 또는 memberId 필터.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| memberId | N | string | 대상 회원 |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 SanctionPage
- 관련 테이블: sanctions
- 목업 연결: adminReports

### 13.5 E_062 제재 해제

- `POST /api/v1/admin/sanctions/{sanctionId}/revoke` · 노현섭 · P1
- 설명: ADMIN만. 사유 필수, 대상 회원 잠금. 이미 해제된 제재는 기존 결과 반환. 다른 유효 제재가 있으면 계속 제한.
- 요청 본문: RevokeSanction
- 성공 응답: 200 Sanction
- 관련 테이블: sanctions, members
- 목업 연결: adminReports

### 13.6 E_069 동네 운영 통계

- `GET /api/v1/admin/statistics` · 노현섭 · P2
- 설명: ADMIN만. from/to 최대 31일 KST 포함. 현재 누적 등록/활성/미처리와 기간 신규/성사율/일자별 요청·인기 완료 건수를 구분. 구체 집계 정의는 설계 규칙 7절.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| communityId | Y | string | 대상 동네 |
| from | Y | string | 시작 |
| to | Y | string | 끝 |

- 요청 본문: 없음
- 성공 응답: 200 AdminStats
- 관련 테이블: items, rentals, members, community_memberships, reports
- 목업 연결: admin

## 14. 공지·정책 (notices·policies)

> `notices`, `정적 리소스`

### 14.1 E_063 동네 공지 목록

- `GET /api/v1/communities/{communityId}/notices` · 노현섭 · P2
- 설명: 유효 소속 회원 또는 ADMIN. 삭제 제외 pinned DESC,createdAt DESC,id DESC.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 NoticePage
- 관련 테이블: notices
- 목업 연결: policy

### 14.2 E_064 공지 상세

- `GET /api/v1/notices/{noticeId}` · 노현섭 · P2
- 설명: 공지 커뮤니티의 유효 소속 회원 또는 ADMIN. 삭제된 공지 404.
- 요청 본문: 없음
- 성공 응답: 200 Notice
- 관련 테이블: notices
- 목업 연결: policy

### 14.3 E_065 관리자 공지 목록

- `GET /api/v1/admin/notices` · 노현섭 · P2
- 설명: ADMIN만. 동네 필터, 삭제 제외.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| communityId | N | string | 동네 필터 |
| page | N | integer | 0부터 시작; 기본 0 |
| size | N | integer | 기본 20; 최대 50 |

- 요청 본문: 없음
- 성공 응답: 200 NoticePage
- 관련 테이블: notices
- 목업 연결: adminNotices

### 14.4 E_066 공지 작성

- `POST /api/v1/admin/notices` · 노현섭 · P2
- 설명: ADMIN만. 활성 커뮤니티. 본문 일반 텍스트로 렌더링하며 임의 HTML 실행 금지.
- 요청 본문: CreateNotice
- 성공 응답: 201 Notice
- 관련 테이블: notices
- 목업 연결: adminNotices

### 14.5 E_067 공지 수정·고정

- `PUT /api/v1/admin/notices/{noticeId}` · 노현섭 · P2
- 설명: ADMIN만. version 검사 후 전체 필드 교체.
- 요청 본문: UpdateNotice
- 성공 응답: 200 Notice
- 관련 테이블: notices
- 목업 연결: adminNotices

### 14.6 E_068 공지 삭제

- `DELETE /api/v1/admin/notices/{noticeId}` · 노현섭 · P2
- 설명: ADMIN만. version 검사 후 논리 삭제.

| Query | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| expectedVersion | Y | integer | 마지막 조회한 version |

- 요청 본문: 없음
- 성공 응답: 204 본문 없음
- 관련 테이블: notices
- 목업 연결: adminNotices

### 14.7 E_070 정책·안내 정적 문서

- `GET /api/v1/policies/{policyKey}` · 노현섭 · P1
- 설명: 공개. 승인된 버전의 정적 문서 반환. 개인정보·약관 본문은 별도 작성/검토 후 제공; 미작성 문서는 404. DB 정책 편집 기능은 범위 밖.
- 요청 본문: 없음
- 성공 응답: 200 PolicyPage
- 관련 테이블: 정적 리소스
- 목업 연결: policy

## 15. 요청·응답 필드 사전

참조 타입의 내부 필드는 같은 이름의 표에서 확인한다. 필수 N은 요청 생략 가능 또는 응답 조건부 포함이다. 응답에서도 null은 사용하지 않는다.

Upload는 multipart/form-data이며 필드 file 하나가 필수다. MIME·용량·권한은 업로드 API 절을 따른다.

### FieldError
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| field | string | Y | 최대 길이 100 |
| reason | string | Y | 최대 길이 300 |
### ApiError
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| code | string | Y | 최대 길이 60 |
| message | string | Y | 최대 길이 300 |
| requestId | string(uuid) | Y | — |
| fieldErrors | FieldError[] | N | — |
| currentVersion | integer(int64) | N | 최소 0 |
| conflictingRentalIds | string[] | N | — |
### CsrfToken
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| csrfToken | string | Y | 최대 길이 128. BREACH 방어로 요청마다 값이 달라지는 마스킹 토큰이며, 세션의 원본 토큰은 유지된다. 받은 값을 `X-CSRF-TOKEN` 헤더로 되돌려주면 되고 캐싱해 재사용할 수 있다 |
### MemberSummary
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| displayName | string | Y | 최대 길이 30 |
| joinedAt | string(date-time) | Y | — |
### Membership
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| communityId | string | Y | ID 문자열 |
| communityName | string | Y | 최대 길이 100 |
| status | string | Y | 값: ACTIVE, LEFT |
| verifiedAt | string(date-time) | Y | — |
| verificationExpiresAt | string(date-time) | Y | — |
| isValid | boolean | Y | — |
### MyProfile
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| member | MemberSummary | Y | — |
| role | string | Y | 값: USER, ADMIN |
| status | string | Y | 값: ACTIVE, WITHDRAWN |
| isRestricted | boolean | Y | — |
| activeMembership | Membership | N | — |
| onboardingRequired | boolean | Y | — |
| version | integer(int64) | Y | 최소 0 |
### PublicProfile
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| member | MemberSummary | Y | — |
| communityName | string | N | 최대 길이 100 |
| isVerified | boolean | Y | — |
| blockedByMe | boolean | Y | — |
### UpdateProfile
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| expectedVersion | integer(int64) | Y | 최소 0 |
| displayName | string | Y | 최소 길이 2; 최대 길이 30 |
### TrustMetrics
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| completedCount | integer(int32) | Y | 최소 0 |
| lentCount | integer(int32) | Y | 최소 0 |
| returnPromiseKept | integer(int32) | Y | 최소 0 |
| returnPromiseTotal | integer(int32) | Y | 최소 0 |
| basis | string | Y | 최대 길이 200 |
### Community
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| name | string | Y | 최대 길이 100 |
| displayAddress | string | Y | 최대 길이 255 |
| centerLatitude | number | Y | 최소 -90; 최대 90 |
| centerLongitude | number | Y | 최소 -180; 최대 180 |
| verificationRadiusM | integer(int32) | Y | 최소 1 |
| verifiedMemberCount | integer(int32) | Y | 최소 0 |
### CommunityPage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | Community[] | Y | — |
| page | integer(int32) | Y | 최소 0 |
| size | integer(int32) | Y | 최소 1; 최대 50 |
| totalElements | integer(int64) | Y | 최소 0 |
| hasNext | boolean | Y | — |
### NearbyCommunities
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | Community[] | Y | — |
| truncated | boolean | Y | — |
### Place
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| communityId | string | Y | ID 문자열 |
| name | string | Y | 최대 길이 100 |
| latitude | number | Y | 최소 -90; 최대 90 |
| longitude | number | Y | 최소 -180; 최대 180 |
| guide | string | N | 최대 길이 300 |
### PlaceList
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | Place[] | Y | — |
### VerifyLocationRequest
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| latitude | number | Y | 최소 -90; 최대 90 |
| longitude | number | Y | 최소 -180; 최대 180 |
| accuracyMeters | number | Y | 최소 0; 최대 100 |
### Category
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| code | string | Y | 값: TOOL, CAMP, TRAVEL, BABY, MUSIC, SPORT, KITCHEN, CLEAN, LIFE, OTHER |
| name | string | Y | 최대 길이 30 |
| sortOrder | integer(int32) | Y | 최소 0 |
### CategoryList
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | Category[] | Y | — |
### MediaFile
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| mimeType | string | Y | 값: image/jpeg, image/png, image/webp |
| byteSize | integer(int32) | Y | 최소 1; 최대 10485760 |
| width | integer(int32) | Y | 최소 1 |
| height | integer(int32) | Y | 최소 1 |
| contentUrl | string | Y | 최대 길이 300 |
| status | string | Y | 값: TEMP, ATTACHED |
| expiresAt | string(date-time) | N | — |
### Image
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| mediaId | string | Y | ID 문자열 |
| sortOrder | integer(int32) | Y | 최소 0; 최대 4 |
| contentUrl | string | Y | 최대 길이 300 |
### CreateItem
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| title | string | Y | 최소 길이 1; 최대 길이 100 |
| description | string | N | 최대 길이 3000 |
| categoryId | string | Y | ID 문자열 |
| placeId | string | Y | ID 문자열 |
| availableStartDate | string(date) | Y | — |
| availableEndDate | string(date) | Y | — |
| imageIds | string[] | Y | 최소 1개; 최대 5개; 중복 금지 |
### UpdateItem
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| title | string | Y | 최소 길이 1; 최대 길이 100 |
| description | string | N | 최대 길이 3000 |
| categoryId | string | Y | ID 문자열 |
| placeId | string | Y | ID 문자열 |
| availableStartDate | string(date) | Y | — |
| availableEndDate | string(date) | Y | — |
| imageIds | string[] | Y | 최소 1개; 최대 5개; 중복 금지 |
| expectedVersion | integer(int64) | Y | 최소 0 |
### VisibilityChange
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| expectedVersion | integer(int64) | Y | 최소 0 |
| visibility | string | Y | 값: PUBLIC, HIDDEN |
### ItemSummary
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| title | string | Y | 최대 길이 100 |
| category | Category | Y | — |
| owner | MemberSummary | Y | — |
| place | Place | Y | — |
| thumbnailUrl | string | N | 최대 길이 300 |
| visibility | string | Y | 값: PUBLIC, HIDDEN, DELETED |
| distanceMeters | integer(int32) | Y | 최소 0 |
| distanceBasis | string | Y | 값: COMMUNITY_CENTER |
| availableForRange | boolean | N | — |
| createdAt | string(date-time) | Y | — |
| pendingRequestCount | integer(int32) | N | 내 물건 관리 목록에서만 포함하는 현재 REQUESTED 수; 최소 0 |
| version | integer(int64) | Y | 최소 0 |
### ItemPage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | ItemSummary[] | Y | — |
| page | integer(int32) | Y | 최소 0 |
| size | integer(int32) | Y | 최소 1; 최대 50 |
| totalElements | integer(int64) | Y | 최소 0 |
| hasNext | boolean | Y | — |
### ItemDetail
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| title | string | Y | 최대 길이 100 |
| description | string | N | 최대 길이 3000 |
| owner | MemberSummary | Y | — |
| communityId | string | Y | ID 문자열 |
| category | Category | Y | — |
| place | Place | Y | — |
| images | Image[] | Y | — |
| availableStartDate | string(date) | Y | — |
| availableEndDate | string(date) | Y | — |
| visibility | string | Y | 값: PUBLIC, HIDDEN, DELETED |
| distanceMeters | integer(int32) | Y | 최소 0 |
| distanceBasis | string | Y | 값: COMMUNITY_CENTER |
| version | integer(int64) | Y | 최소 0 |
| allowedActions | string[] | Y | — |
| createdAt | string(date-time) | Y | — |
### DateRange
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| startDate | string(date) | Y | — |
| endDate | string(date) | Y | — |
### Availability
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| itemId | string | Y | ID 문자열 |
| availableStartDate | string(date) | Y | — |
| availableEndDate | string(date) | Y | — |
| occupiedRanges | DateRange[] | Y | — |
| overdueBlocked | boolean | Y | — |
| checkedAt | string(date-time) | Y | — |
| selectedRangeAvailable | boolean | N | — |
### MapPlace
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| place | Place | Y | — |
| matchingItemCount | integer(int32) | Y | 최소 0 |
| distanceMeters | integer(int32) | Y | 최소 0 |
### MapResults
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | MapPlace[] | Y | — |
| truncated | boolean | Y | — |
| distanceBasis | string | Y | 값: COMMUNITY_CENTER |
### RentalCounts
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| requested | integer(int32) | Y | 최소 0 |
| approved | integer(int32) | Y | 최소 0 |
| active | integer(int32) | Y | 최소 0 |
| overdue | integer(int32) | Y | 최소 0 |
| returned | integer(int32) | Y | 최소 0 |
| rejected | integer(int32) | Y | 최소 0 |
| canceled | integer(int32) | Y | 최소 0 |
| expired | integer(int32) | Y | 최소 0 |
### RentalSummary
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| itemId | string | Y | ID 문자열 |
| itemTitle | string | Y | 최대 길이 100 |
| owner | MemberSummary | Y | — |
| borrower | MemberSummary | Y | — |
| startDate | string(date) | Y | — |
| endDate | string(date) | Y | — |
| status | string | Y | 값: REQUESTED, APPROVED, REJECTED, CANCELED, ACTIVE, RETURNED, EXPIRED |
| overdue | boolean | Y | — |
| dDay | integer | Y | — |
| placeName | string | Y | 최대 길이 100 |
| version | integer(int64) | Y | 최소 0 |
| allowedActions | string[] | Y | — |
| createdAt | string(date-time) | Y | — |
### RentalPage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | RentalSummary[] | Y | — |
| page | integer(int32) | Y | 최소 0 |
| size | integer(int32) | Y | 최소 1; 최대 50 |
| totalElements | integer(int64) | Y | 최소 0 |
| hasNext | boolean | Y | — |
### History
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| fromStatus | string | N | 값: REQUESTED, APPROVED, REJECTED, CANCELED, ACTIVE, RETURNED, EXPIRED |
| toStatus | string | Y | 값: REQUESTED, APPROVED, REJECTED, CANCELED, ACTIVE, RETURNED, EXPIRED |
| actorId | string | N | ID 문자열 |
| reason | string | N | 최대 길이 500 |
| rentalVersion | integer(int64) | Y | 최소 0 |
| createdAt | string(date-time) | Y | — |
### AppointmentProposal
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| slotId | string | Y | ID 문자열 |
| proposerId | string | Y | ID 문자열 |
| kind | string | Y | 값: PICKUP, RETURN |
| scheduledAt | string(date-time) | Y | — |
| place | Place | Y | — |
| status | string | Y | 값: PROPOSED, CONFIRMED, SUPERSEDED, WITHDRAWN |
| acceptedBy | string | N | ID 문자열 |
| acceptedAt | string(date-time) | N | — |
### AppointmentSlot
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| rentalId | string | Y | ID 문자열 |
| kind | string | Y | 값: PICKUP, RETURN |
| version | integer(int64) | Y | 최소 0 |
| confirmedProposal | AppointmentProposal | N | — |
| pendingProposal | AppointmentProposal | N | — |
| allowedActions | string[] | Y | — |
### AppointmentSlots
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | AppointmentSlot[] | Y | — |
### RentalDetail
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| rental | RentalSummary | Y | — |
| reason | string | N | 최대 길이 500 |
| requestExpiresAt | string(date-time) | Y | — |
| dueAt | string(date-time) | Y | — |
| approvedAt | string(date-time) | N | — |
| handedOverAt | string(date-time) | N | — |
| returnedAt | string(date-time) | N | — |
| chatRoomId | string | N | ID 문자열 |
| history | History[] | Y | — |
| appointments | AppointmentSlot[] | Y | — |
### CreateRental
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| itemId | string | Y | ID 문자열 |
| startDate | string(date) | Y | — |
| endDate | string(date) | Y | — |
### VersionCommand
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| expectedVersion | integer(int64) | Y | 최소 0 |
### ReasonCommand
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| expectedVersion | integer(int64) | Y | 최소 0 |
| reason | string | Y | 공백만 입력 불가; 최대 길이 500 |
### CancelCommand
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| expectedVersion | integer(int64) | Y | 최소 0 |
| reason | string | Y | 공백만 입력 불가; 최대 길이 500 |
### ProposeAppointment
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| expectedVersion | integer(int64) | Y | 최소 0 |
| scheduledAt | string(date-time) | Y | — |
| placeId | string | Y | ID 문자열 |
### MyRentalSummary
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| borrowed | RentalCounts | Y | — |
| lent | RentalCounts | Y | — |
| nextRentals | RentalSummary[] | Y | — |
### Home
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| community | Community | Y | — |
| itemCount | integer(int32) | Y | 최소 0 |
| categories | Category[] | Y | — |
| latestItems | ItemSummary[] | Y | — |
| myRentals | MyRentalSummary | Y | — |
| unreadNotificationCount | integer(int32) | Y | 최소 0 |
| unreadChatCount | integer(int32) | Y | 최소 0 |
### CreateRoom
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| itemId | string | Y | ID 문자열 |
### ChatAccess
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| canSend | boolean | Y | — |
| allowedRentalIds | string[] | Y | — |
| reason | string | Y | 값: NORMAL, BLOCKED, RESTRICTED, COMMUNITY_CHANGED, ITEM_UNAVAILABLE, EXISTING_RENTAL_ONLY |
### ChatRoom
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| itemId | string | Y | ID 문자열 |
| itemTitle | string | Y | 최대 길이 100 |
| otherMember | MemberSummary | Y | — |
| unreadCount | integer(int32) | Y | 최소 0 |
| lastMessagePreview | string | N | 최대 길이 200 |
| lastMessageAt | string(date-time) | N | — |
| rentals | RentalSummary[] | Y | — |
| access | ChatAccess | Y | — |
### ChatRoomPage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | ChatRoom[] | Y | — |
| page | integer(int32) | Y | 최소 0 |
| size | integer(int32) | Y | 최소 1; 최대 50 |
| totalElements | integer(int64) | Y | 최소 0 |
| hasNext | boolean | Y | — |
### SystemPayload
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| eventType | string | Y | 최대 길이 50 |
| aggregateVersion | integer(int64) | Y | 최소 0 |
| occurredAt | string(date-time) | Y | — |
| label | string | Y | 최대 길이 200 |
### Message
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| roomId | string | Y | ID 문자열 |
| sequence | integer(int64) | Y | 최소 0 |
| type | string | Y | 값: TEXT, SYSTEM |
| senderId | string | N | ID 문자열 |
| body | string | N | 최대 길이 2000 |
| rentalId | string | N | ID 문자열 |
| appointmentProposalId | string | N | ID 문자열 |
| clientMessageId | string(uuid) | N | — |
| system | SystemPayload | N | — |
| createdAt | string(date-time) | Y | — |
### MessagePage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | Message[] | Y | — |
| hasMore | boolean | Y | — |
| nextAfterSequence | integer(int64) | N | 최소 0 |
| nextBeforeSequence | integer(int64) | N | 최소 0 |
| otherLastReadSequence | integer(int64) | Y | 최소 0 |
### SendMessage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| clientMessageId | string(uuid) | Y | — |
| body | string | Y | 최소 길이 1; 최대 길이 2000 |
| rentalId | string | N | ID 문자열 |
### ReadPosition
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| lastReadSequence | integer(int64) | Y | 최소 0 |
### UnreadCount
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| unreadCount | integer(int32) | Y | 최소 0 |
### Notification
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| type | string | Y | 최대 길이 50 |
| title | string | Y | 최대 길이 200 |
| targetType | string | Y | 값: RENTAL, REPORT, MEMBER_SELF |
| targetId | string | Y | ID 문자열 |
| readAt | string(date-time) | N | — |
| createdAt | string(date-time) | Y | — |
### NotificationPage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | Notification[] | Y | — |
| hasMore | boolean | Y | — |
| nextCursor | string | N | ID 문자열 |
### ReadAllNotifications
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| throughId | string | Y | ID 문자열 |
### Block
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| member | MemberSummary | Y | — |
| blockedAt | string(date-time) | Y | — |
### BlockPage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | Block[] | Y | — |
| page | integer(int32) | Y | 최소 0 |
| size | integer(int32) | Y | 최소 1; 최대 50 |
| totalElements | integer(int64) | Y | 최소 0 |
| hasNext | boolean | Y | — |
### CreateReport
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| targetType | string | Y | 값: MEMBER, ITEM, MESSAGE |
| targetId | string | Y | ID 문자열 |
| reasonCode | string | Y | 값: MONEY_REQUEST, ITEM_MISMATCH, NO_SHOW, ABUSE, FRAUD, OTHER |
| detail | string | N | 최대 길이 1000 |
### Report
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| targetType | string | Y | 값: MEMBER, ITEM, MESSAGE |
| targetId | string | Y | ID 문자열 |
| targetMember | MemberSummary | Y | — |
| reasonCode | string | Y | 값: MONEY_REQUEST, ITEM_MISMATCH, NO_SHOW, ABUSE, FRAUD, OTHER |
| detail | string | N | 최대 길이 1000 |
| status | string | Y | 값: PENDING, RESOLVED |
| publicReason | string | N | 최대 길이 500 |
| action | string | N | 값: WARN, SUSPEND_7D, SUSPEND_30D, BAN, DISMISS |
| version | integer(int64) | Y | 최소 0 |
| createdAt | string(date-time) | Y | — |
### ReportPage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | Report[] | Y | — |
| page | integer(int32) | Y | 최소 0 |
| size | integer(int32) | Y | 최소 1; 최대 50 |
| totalElements | integer(int64) | Y | 최소 0 |
| hasNext | boolean | Y | — |
### AdminReport
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| report | Report | Y | — |
| reporter | MemberSummary | Y | — |
| communityId | string | Y | ID 문자열 |
| evidenceText | string | N | 최대 길이 3000 |
| internalNote | string | N | 최대 길이 2000 |
### AdminReportPage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | AdminReport[] | Y | — |
| page | integer(int32) | Y | 최소 0 |
| size | integer(int32) | Y | 최소 1; 최대 50 |
| totalElements | integer(int64) | Y | 최소 0 |
| hasNext | boolean | Y | — |
### ResolveReport
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| expectedVersion | integer(int64) | Y | 최소 0 |
| action | string | Y | 값: WARN, SUSPEND_7D, SUSPEND_30D, BAN, DISMISS |
| publicReason | string | Y | 최소 길이 1; 최대 길이 500 |
| internalNote | string | N | 최대 길이 2000 |
### Sanction
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| memberId | string | Y | ID 문자열 |
| kind | string | Y | 값: SUSPEND, BAN |
| startsAt | string(date-time) | Y | — |
| endsAt | string(date-time) | N | — |
| revokedAt | string(date-time) | N | — |
| isEffective | boolean | Y | — |
### SanctionPage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | Sanction[] | Y | — |
| page | integer(int32) | Y | 최소 0 |
| size | integer(int32) | Y | 최소 1; 최대 50 |
| totalElements | integer(int64) | Y | 최소 0 |
| hasNext | boolean | Y | — |
### RevokeSanction
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| reason | string | Y | 최소 길이 1; 최대 길이 500 |
### Notice
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| id | string | Y | ID 문자열 |
| communityId | string | Y | ID 문자열 |
| title | string | Y | 최대 길이 150 |
| body | string | Y | 최대 길이 10000 |
| pinned | boolean | Y | — |
| version | integer(int64) | Y | 최소 0 |
| createdAt | string(date-time) | Y | — |
### NoticePage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| items | Notice[] | Y | — |
| page | integer(int32) | Y | 최소 0 |
| size | integer(int32) | Y | 최소 1; 최대 50 |
| totalElements | integer(int64) | Y | 최소 0 |
| hasNext | boolean | Y | — |
### CreateNotice
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| communityId | string | Y | ID 문자열 |
| title | string | Y | 최소 길이 1; 최대 길이 150 |
| body | string | Y | 최소 길이 1; 최대 길이 10000 |
| pinned | boolean | Y | — |
### UpdateNotice
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| expectedVersion | integer(int64) | Y | 최소 0 |
| title | string | Y | 최소 길이 1; 최대 길이 150 |
| body | string | Y | 최소 길이 1; 최대 길이 10000 |
| pinned | boolean | Y | — |
### DailyCount
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| date | string(date) | Y | — |
| count | integer(int32) | Y | 최소 0 |
### CategoryCount
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| category | Category | Y | — |
| count | integer(int32) | Y | 최소 0 |
### PopularItem
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| itemId | string | Y | ID 문자열 |
| title | string | Y | 최대 길이 100 |
| returnedCount | integer(int32) | Y | 최소 0 |
### AdminStats
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| communityId | string | Y | ID 문자열 |
| from | string(date) | Y | — |
| to | string(date) | Y | — |
| registeredItems | integer(int32) | Y | 최소 0 |
| activeRentals | integer(int32) | Y | 최소 0 |
| newItems | integer(int32) | Y | 최소 0 |
| newMembers | integer(int32) | Y | 최소 0 |
| requestCount | integer(int32) | Y | 최소 0 |
| successfulRequestCount | integer(int32) | Y | 최소 0 |
| successRate | number | Y | 최소 0; 최대 100 |
| pendingReports | integer(int32) | Y | 최소 0 |
| dailyRentalCounts | DailyCount[] | Y | — |
| categoryCounts | CategoryCount[] | Y | — |
| popularItems | PopularItem[] | Y | — |
### PolicyPage
| 필드 | 타입 | 필수 | 제약·의미 |
| --- | --- | --- | --- |
| key | string | Y | 값: community, safety, prohibited, damage, disputes, trust, terms, privacy, support, about |
| title | string | Y | 최대 길이 100 |
| body | string | Y | 최대 길이 20000 |
| versionLabel | string | Y | 최대 길이 30 |

## 16. 호출 예시

아래 요청은 유효 세션 쿠키와 X-CSRF-TOKEN을 갖춘 것으로 가정한다. POST는 매 명령마다 새 Idempotency-Key를 만들고 네트워크 재시도에서는 같은 키를 재사용한다. ID·version은 실제 조회 결과로 바꾼다.
### 물건 등록
POST /api/v1/items
```json
{"title":"충전식 전동드릴 12V","description":"배터리 2개 포함","categoryId":"1","placeId":"11","availableStartDate":"2026-10-01","availableEndDate":"2026-10-31","imageIds":["301","302"]}
```
201 ItemDetail 응답의 id와 version을 저장한다. 사진은 먼저 POST /media로 올린 본인의 TEMP 파일이어야 한다.
### 대여 요청과 승인
POST /api/v1/rentals
```json
{"itemId":"101","startDate":"2026-10-03","endDate":"2026-10-04"}
```
201 RentalDetail의 rental.id=501, rental.status=REQUESTED, rental.version=0인 예라면 소유자는 POST /api/v1/rentals/501/approve에 아래를 전달한다.
```json
{"expectedVersion":0}
```
200 RentalDetail의 status=APPROVED,version=1. 겹치는 다른 요청과 동시에 승인하면 한 건만 성공하며 다른 요청은 409 RENTAL_DATE_CONFLICT를 받는다.
### 약속 제안과 수락
GET /api/v1/rentals/501/appointments로 PICKUP 슬롯 version=0을 얻었다면 POST /api/v1/rentals/501/appointments/PICKUP/proposals:
```json
{"expectedVersion":0,"scheduledAt":"2026-10-03T01:00:00Z","placeId":"11"}
```
KST 10월 3일 오전 10시다. 응답 pendingProposal.id=701,slot.version=1이면 상대가 POST /api/v1/appointments/701/accept에 {“expectedVersion”:1}을 전달한다. 여기 version은 대여 version이 아니다.
### 메시지 재전송
POST /api/v1/chat/rooms/901/messages
```json
{"clientMessageId":"6d68e9e7-0337-4c5d-bd9b-9d267f23ba16","body":"오전 10시에 공용 택배함 앞에서 뵙겠습니다.","rentalId":"501"}
```
응답을 못 받은 재시도는 같은 Idempotency-Key와 clientMessageId를 유지한다. 메시지 본문을 바꿔 새로 보내는 경우에는 둘 다 새로 만든다.
### 커서와 모두 읽음
GET /api/v1/chat/rooms/901/messages?afterSequence=25&limit=50으로 25 이후 메시지를 순서대로 읽는다. hasMore=true이면 nextAfterSequence로 계속 조회한다. PUT /api/v1/chat/rooms/901/read에 {“lastReadSequence”:30}을 보낸다.
알림 목록에서 가장 큰 본인 알림 ID가 801이었다면 PUT /api/v1/notifications/read-all에 {“throughId”:“801”}을 보내며 이후 도착한 802는 미열람으로 남는다.

## 17. 화면과 API 연결

| 목업 화면 | 호출 순서 또는 기능 |
| --- | --- |
| login | csrf → kakao → callback → me |
| join/verify/joindone | communities 또는 nearby → verifications → me |
| home | home, 알림/채팅 unread-count |
| register | categories + places → media → items 등록/수정 |
| detail/request | item detail + availability → rentals 생성 |
| search | items 목록 / items/map → 장소 선택 시 placeId 검색 |
| rentals | rentals?role + summary → 거래 명령 → 최신 detail |
| chat | rooms → messages 커서 → send/read; 약속은 C의 appointments API |
| profile/profileOther | me/public profile + trust + member items |
| notifications | notifications → read/read-all → target별 최신 resource |
| blocked/reports | blocks 목록/해제 + 내 reports |
| adminReports | admin reports → detail → resolve, sanctions/revoke |
| admin/adminNotices | statistics + admin notices CRUD |
| policy/footer | policies/{policyKey}; UI/테마는 별도 API 없음 |

## 18. 구현 인수 조건

- 같은 날짜에 겹치는 두 승인 동시 실행 시 한 건만 성공. 승인/취소·물건 기간 수정/승인도 경합 테스트.
- 종료일=다음 시작일은 충돌, 반납 후 약정 기간 점유 유지, 연체 시 후속 전달 차단.
- 다른 회원·다른 동네·비공개 이미지 URL·임의 room/transaction ID로 접근하면 정책대로 거부.
- 슬롯 변경/수락 경합, 오래된 proposal 수락, 자기 제안 수락 거부.
- 메시지 응답 유실 재전송 후 한 건만 저장, 커서 조회 누락 없음, 읽음 위치 감소 없음.
- 롤백한 거래 알림 없음, outbox 재소비에도 수신자 알림/시스템 메시지 중복 없음.
- 차단/제재/동네 변경 후 기존 거래 정리 가능, 신규 관계는 거부.
- 모두 읽음과 신규 알림 수신이 경합해도 기준 ID 이후 알림은 미열람.
- 각 에러의 code·status·응답 필드가 OpenAPI와 일치.
