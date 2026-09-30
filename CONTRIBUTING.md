# BILLIM 팀 컨벤션

> 버전 : v0.1.0\
> 수정일 : 2026.09.29

## 1. 작업 원칙

- PR은 리뷰어가 한 번에 읽고 판단할 수 있는 크기로 만든다. 규모가 커지면 독립적으로 머지할 수 있는 단위로 분할한다.
- 이슈는 필요할 때만 만든다. 만든 이슈는 하나의 PR로 닫는다.
- 하나의 PR은 하나의 목적만 가진다. 작업 범위 밖의 리팩터링·정리는 별도 PR로 분리한다.
- 구현은 명세(`docs/erd/`, `docs/api/`)를 따르고, 명세에 없는 추상화·확장 지점을 미리 만들지 않는다.
- 코드와 문서가 어긋나면 같은 PR에서 문서까지 갱신한다. 명세가 원본이다.

## 2. 브랜치와 커밋

### 작업 타입

GitHub 라벨과 같은 목록을 쓴다. 이슈·PR 제목의 접두사로 라벨이 자동으로 붙는다.

| 타입 | 용도 |
| --- | --- |
| `feat` | 새로운 기능 추가 및 확장 |
| `fix` | 기존 버그 및 오류 수정 |
| `refactor` | 동작 변경 없는 코드 구조 개선 |
| `test` | 테스트 코드 작성 및 수정 |
| `docs` | 문서 작성 및 수정 |
| `ci` | 빌드 및 배포 자동화(CI/CD) 설정 |
| `chore` | 설정, 패키지 관리 및 기타 작업 |

### 이름 규칙

- 이슈·PR·커밋 제목: `type: 한국어 요약`. `추가했습니다`가 아니라 `추가`처럼 명사형으로 끝낸다.
- 브랜치: `<type>/<기능명-영문-kebab>`. 이슈와 연결할 때는 `<type>/<이슈번호>-<기능명>`.

```text
feat: 대여 요청 추가
feat/rental-request      # 기본
feat/12-rental-request   # 이슈와 연결하는 경우
```

### 커밋

- 하나의 커밋은 하나의 변경 이유만 담는다. 기능·수정·리팩터링·테스트를 한 커밋에 섞지 않는다.
- 제목으로 설명이 부족하면 변경 이유와 영향 범위를 본문에 단답식으로 남긴다.

## 3. 이슈와 PR

- 이슈를 닫는 PR은 본문에 `Closes #12`를 쓴다. 여러 PR로 나눴다면 마지막 PR만 `Closes`, 나머지는 `Related to #12`.
- PR 본문은 템플릿(작업 내용·테스트·체크리스트)을 채운다. 체크리스트의 문서 갱신 항목이 곧 이 컨벤션이다.

## 4. 리뷰와 머지

1. `develop`에서 브랜치를 만든다.
2. `develop` 대상 PR을 올리면 CodeRabbit이 자동으로 먼저 리뷰한다.
3. CodeRabbit 의견은 검토 대상이지 통과 조건이 아니다. 반영하지 않은 의견에는 이유를 답글로 남긴다.
4. CI 통과 + 팀원 1명 승인이 되면 `develop`에 Squash merge 한다.
5. 배포 시점에 `develop` → `main` PR을 만들고 1명 승인 후 머지한다.
6. `main`과 `develop`에는 직접 push 하지 않는다.

리뷰어가 보는 것: 작업 목적 달성(이슈가 있으면 완료 조건 충족), 명세·문서 갱신 여부, 비밀값 노출 여부.

## 5. 백엔드 코드

### 구조

- 도메인 패키지는 ERD의 도메인 그룹과 1:1이다: `member` `community` `item` `rental` `chat` `notification` `safety`
- 각 도메인 아래는 `controller` `service` `repository` `entity` `dto`로 나눈다.
- 여러 도메인이 함께 쓰는 코드만 `global`(`config` `security` `exception` `response`)에 둔다.
- Controller는 요청 검증·인증 정보 전달·응답 변환, Service는 유스케이스와 트랜잭션 경계, Repository는 조회와 저장까지만 맡는다.
- Entity는 API 요청·응답에 직접 노출하지 않는다.

### 네이밍

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| Java 클래스 | PascalCase | `RentalService`, `ErrorResponse` |
| Java 필드·메서드 | camelCase | `rentalId`, `requestExpiresAt` |
| DB 테이블 | snake_case 복수형 | `rentals`, `item_images`, `rental_status_histories` |
| DB 컬럼 | snake_case | `request_expires_at`, `owner_id` |
| API 경로 | 소문자 복수형 리소스 | `/api/v1/items/{itemId}`, `/api/v1/communities/{communityId}/places` |

- Java 필드와 컬럼이 기본 매핑(camelCase ↔ snake_case)을 벗어나면 `@Column(name = "...")`으로 명시한다.
- 테이블·컬럼·경로 이름의 원본은 ERD와 API 명세다. 새 이름이 필요하면 이 규칙으로 짓고 명세에 추가한다.

### 구현

- 의존성은 생성자 주입을 쓴다. (`@RequiredArgsConstructor` 허용)
- 조회는 `@Transactional(readOnly = true)`, 변경은 `@Transactional`.
- 형식 검증은 Bean Validation, 도메인 규칙 검증은 Service·Entity의 몫이다.
- Entity는 공개 setter 대신 의미가 드러나는 상태 변경 메서드를 쓰고, 기본 생성자는 `PROTECTED`로 제한한다.
- JPA 연관관계는 지연 로딩이 기본이다.
- Lombok은 필요한 어노테이션만 쓴다. `@Data` 금지.

## 6. DB 마이그레이션

- 스키마 변경은 Flyway 마이그레이션으로만 한다. `ddl-auto: validate`를 바꾸지 않는다.
- 파일 위치와 이름: `src/main/resources/db/migration/V{번호}__{설명_영문_snake}.sql`
- 번호는 순차 정수다. **커밋과 PR 전에 `develop`의 최신 번호를 확인하고 그다음 번호를 쓴다.** 리뷰 중에 다른 PR이 같은 번호로 먼저 머지되면 내 번호를 올려서 다시 올린다.
- 머지된 마이그레이션 파일은 수정·삭제하지 않는다. 잘못됐다면 바로잡는 새 버전을 추가한다. (적용된 파일을 고치면 checksum 불일치로 서버가 뜨지 않는다)
- 마이그레이션 PR은 [도메인 및 DB 설계](docs/erd/도메인%20및%20DB%20설계.md) 갱신을 포함한다.

## 7. 테스트

- 기능 추가와 버그 수정 PR은 관련 테스트를 포함한다. 정상 흐름과 예외 흐름을 함께 검증한다.
- 겹침 판정, 상태 전이, 동시 승인처럼 정합성이 걸린 규칙은 경계 조건까지 검증한다.
- 구현 세부사항이 아니라 밖에서 관찰되는 동작을 검증한다.
- DB가 필요한 테스트는 Testcontainers MySQL을 쓴다. 
- 테스트 메서드 이름은 영어, `@DisplayName`은 한국어.
- 커버리지 숫자보다 핵심 동작과 경계 조건이 우선이다.

## 8. 예외와 로그

### 예외

- 예상 가능한 실패는 `BusinessException` + `ErrorCode`로 표현하고, `GlobalExceptionHandler`가 오류 응답으로 변환한다. Controller에서 개별 `try-catch`로 응답을 만들지 않는다.
- 새 에러 코드는 [에러 코드](docs/api/에러%20코드.md)에 같은 PR에서 추가한다.
- 예외를 잡아 삼키지 않는다. 다른 예외로 변환할 때는 원인을 보존한다.
- 클라이언트 오류(4xx)와 서버 오류(5xx)를 상태 코드로 구분하고, 내부 클래스명·SQL·스택 트레이스를 응답에 내보내지 않는다.

### 로그

- SLF4J와 `{}` 자리표시자를 쓴다. `System.out`·`printStackTrace` 금지.
- 오류 응답의 `requestId`를 로그에도 함께 남긴다. 사용자가 받은 오류와 서버 로그를 잇는 열쇠다.
- 수준: 주요 상태 변화 `info`, 확인이 필요한 상황 `warn`, 예상 밖 오류 `error`, 개발용 `debug`.
- 비밀번호·세션 ID·개인정보를 로그에 남기지 않는다.

## 9. API

- API의 원본은 [API 명세서](docs/api/API%20명세서.md)다. 경로·필드 표기·페이지네이션·오류 형식 같은 공통 규약은 명세 0장을 따르고 여기서 반복하지 않는다.
- 명세에 없는 API를 먼저 구현하지 않는다. 계약이 바뀌면 명세·DTO·테스트를 같은 PR에서 고친다.
- 요청 DTO와 응답 DTO를 분리한다.

## 10. 성능

- 측정 없는 최적화를 하지 않는다. 비교를 위해 일부러 비효율적인 코드를 만들지도 않는다.
- 성능 개선은 `docs/performance/`에 `문제 → 근거 → 변경 → 결과 → 한계` 순서로 남긴다.

## 11. 의존성

- 새 라이브러리·미들웨어·인프라는 팀 논의를 거친 뒤 도입한다.
- 도입 이유와 영향 범위를 PR에 작성한다.
- 같은 역할의 라이브러리를 중복 도입하지 않는다.

## 12. 문서와 주석

- 주석과 저장소 문서는 한국어, 코드 식별자는 영어로 쓴다.
- 주석은 코드가 말하지 못하는 것(제약, 이유, 예외 상황)만 명사형으로 적는다.
- Javadoc은 도메인 규칙·복잡한 흐름 같은 핵심 로직에만 쓴다.
- 코드가 바뀌어 주석이 틀리면 같은 PR에서 고치거나 지운다. `TODO`에는 이슈 번호와 제거 조건을 함께 쓴다.
