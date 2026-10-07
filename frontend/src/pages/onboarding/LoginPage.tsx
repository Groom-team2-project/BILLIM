import styles from "@/pages/onboarding/LoginPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 카카오 로그인 시작. Vite 프록시 경유 */
const KAKAO_LOGIN_URL = "/oauth2/authorization/kakao";

export function LoginPage() {
  // 카카오 도메인을 거치는 흐름이라 SPA 라우팅이 아닌 브라우저 최상위 이동
  const startKakao = () => {
    window.location.href = KAKAO_LOGIN_URL;
  };

  return (
    <main className={c("login")}>
      <div className={c("login-hero")}>
        <h1 className={c("login-wordmark")}>BILLIM</h1>
        <p className={c("t-body-lg login-tagline")}>잠깐 필요한 물건, 이웃에게 무료로 빌려요.</p>
      </div>
      <div className={c("login-actions")}>
        <button type="button" className={c("login-kakao t-label")} onClick={startKakao}>
          <KakaoMark />
          카카오로 시작하기
        </button>
        {/* 네이버는 후순위 — 백엔드 미구현 (ERD social_accounts.provider) */}
        <button type="button" className={c("login-naver t-label")} disabled>
          <span className={c("login-naver-mark")}>N</span>
          네이버로 시작하기
        </button>
        <p className={c("t-caption login-hint")}>간편하게 시작하고, 다음 화면에서 우리 동네를 인증해요.</p>
      </div>
    </main>
  );
}

function KakaoMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 3C6.75 3 2.5 6.36 2.5 10.5c0 2.64 1.73 4.96 4.35 6.29-.19.71-.7 2.57-.8 2.97-.12.5.18.49.39.36.16-.11 2.53-1.72 3.55-2.42.65.1 1.32.15 2.01.15 5.25 0 9.5-3.36 9.5-7.35S17.25 3 12 3z" />
    </svg>
  );
}
