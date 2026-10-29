-- 측정용 로그인 세션. 쿠키 원문 'perf-session-0001', token_hash는 SHA-256. 로컬 전용
INSERT INTO auth_sessions (member_id, token_hash, csrf_token, last_seen_at, expires_at, absolute_expires_at, created_at, updated_at)
SELECT MIN(id), SHA2('perf-session-0001', 256), 'perf-csrf', UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6) + INTERVAL 30 DAY, UTC_TIMESTAMP(6) + INTERVAL 30 DAY, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM members WHERE display_name LIKE '부하회원%';
