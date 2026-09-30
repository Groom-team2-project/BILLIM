# 백엔드 AI 작업 지침

`backend/**`를 변경할 때 `docs/ai/README.md`와 함께 적용한다.
코딩 컨벤션은 `CONTRIBUTING.md`의 백엔드 항목을 원본으로 사용한다.

## 작업 전 확인

- 변경할 도메인의 Controller, Service, Repository, Entity, DTO와 인접 테스트의 경계를 먼저 확인한다.
- 테이블 정의는 `docs/erd/`, 엔드포인트 계약은 `docs/api/` 문서를 원본으로 확인한다.
- API 변경이 `docs/api/` 문서와 프론트엔드 계약에 미치는 영향을 확인한다.
- Gradle은 저장소에 포함된 Wrapper(`./gradlew`)를 사용한다.
- 실제 환경 변수와 운영 설정은 승인 없이 열거나 수정하지 않는다.

## 구현

- DB 스키마 변경은 Flyway 마이그레이션(`src/main/resources/db/migration`)으로만 수행하고, 계획 승인 후 진행한다. (`ddl-auto: validate` 유지)
- 인증은 세션 + 카카오 OAuth2 로그인 기준. JWT를 도입하지 않는다.
- 물건 사진은 서버 볼륨에 저장한다. S3 등 외부 스토리지를 도입하지 않는다.
- H2를 사용하지 않는다. 로컬은 Docker MySQL, 테스트는 Testcontainers MySQL 기준.
- API 계약을 변경하면 요청·응답 DTO, `docs/api/` 문서, 테스트를 함께 갱신한다.

## 검증

```bash
cd backend
./gradlew test    # 테스트만
./gradlew build   # 전체 (테스트 포함)
```

테스트는 Testcontainers를 사용하므로 Docker가 실행 중이어야 한다.
