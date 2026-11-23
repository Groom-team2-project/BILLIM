-- 만료 세션 정리의 삭제 조건 컬럼에 색인 없음 — 테이블 전체 스캔.
-- 인증은 모든 요청이 이 테이블을 조회하므로 정리 중 스캔이 응답시간에 영향.
CREATE INDEX ix_auth_sessions_absolute_expires ON auth_sessions (absolute_expires_at);
