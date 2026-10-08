-- OAuth 인가 요청을 세션이 아닌 DB에 보관하기 위한 컬럼.
-- 다중 인스턴스에서 로그인 시작과 콜백이 다른 서버로 가도 토큰 교환 성립 필요.

-- 기존 행은 새 컬럼 값이 없어 콜백에 쓸 수 없다. NOT NULL 추가 시 빈 문자열이 채워지는 것을 피해 먼저 정리.
DELETE FROM oauth_login_attempts;

ALTER TABLE oauth_login_attempts
    -- PKCE 검증값. 콜백 토큰 교환에 원본을 다시 제시해야 하며 난수라 재계산 불가.
    -- PKCE 미사용 제공자 대비로 NULL 허용.
    ADD COLUMN code_verifier VARCHAR(128) NULL COMMENT 'PKCE code_verifier',
    -- 인가 요청에 실어 보낸 값. 콜백에서 동일해야 제공자가 토큰 발급.
    ADD COLUMN redirect_uri VARCHAR(500) NOT NULL COMMENT '인가 요청 redirect_uri';
