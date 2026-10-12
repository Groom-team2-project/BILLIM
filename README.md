# BILLIM

> 같은 동네·아파트 단지 이웃끼리 생활용품을 필요한 날짜에 **무료로** 빌리고 빌려주는 서비스.

## 구성

- `backend/` — Spring Boot API 서버
- `frontend/` — React + TypeScript + Vite

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 4.1, Gradle, Spring Security, Spring Data JPA |
| 프론트엔드 | React, TypeScript, Vite |
| 데이터 | MySQL 8.4, Flyway |
| 인증 | 세션 + 카카오 OAuth2 |
| 테스트 | JUnit 5, Spring Boot Test, Testcontainers |
| 관측 | Actuator, Micrometer, Prometheus |
| API 문서 | OpenAPI · Swagger UI (springdoc) |
| 개발·CI | Docker Compose, GitHub Actions |

## 문서

- [도메인 및 DB 설계](docs/erd/도메인%20및%20DB%20설계.md)
- [API 명세서](docs/api/API%20명세서.md) · [에러 코드](docs/api/에러%20코드.md)
- [정책 및 상태](docs/policy/정책%20및%20상태.md) · [도메인 가이드](docs/policy/도메인%20가이드.md)
- [인증 방식 결정](docs/security/인증%20방식%20결정.md) · [인증 검증 및 보안 점검](docs/security/인증%20검증%20및%20보안%20점검.md)
- [팀 컨벤션](CONTRIBUTING.md) · [AI 작업 지침](AGENTS.md)
