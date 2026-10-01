import { useNavigate } from "react-router-dom";
import { login } from "@/stores/session";
import styles from "@/pages/onboarding/LoginPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

export function LoginPage() {
  const navigate = useNavigate();
  const start = () => {
    login();
    navigate("/join");
  };
  return (
    <main className={c("login")}>
      <div className={c("login-hero")}>
        <h1 className={c("login-wordmark")}>BILLIM</h1>
        <p className={c("t-body-lg login-tagline")}>잠깐 필요한 물건, 이웃에게 무료로 빌려요.</p>
      </div>
      <div className={c("login-actions")}>
        <button type="button" className={c("login-kakao t-label")} onClick={start}>
          <KakaoMark />
          카카오로 시작하기
        </button>
        <button type="button" className={c("login-naver t-label")} onClick={start}>
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
