import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { adminLogin } from "@/api/auth";
import { ApiError, resetCsrf } from "@/api/client";
import { getMyProfile } from "@/api/members";
import { signIn } from "@/stores/session";
import { Alert } from "@/components/ui/alert/Alert";
import styles from "@/pages/onboarding/AdminLoginPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 서버 메시지를 그대로 쓰지 않는 코드만 교체 — 404는 비활성 상태라 원문이 어리둥절함 */
const ERROR_MESSAGES: Record<string, string> = {
  RESOURCE_NOT_FOUND: "관리자 로그인이 비활성 상태예요. 서버 설정을 확인해 주세요.",
};

/** 관리자 전용 로그인 — 소셜 계정 없이 서버 자격증명으로 진입 (백엔드 AdminLoginService) */
export function AdminLoginPage() {
  const navigate = useNavigate();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      await adminLogin(loginId, password);
      // 로그인으로 세션 회전 — 이전 세션의 CSRF 토큰 무효 (AuthCompletePage와 동일)
      resetCsrf();
      const profile = await getMyProfile();
      signIn(profile.role, profile.member.id);
      navigate("/admin", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? (ERROR_MESSAGES[err.code] ?? err.message) : "로그인하지 못했어요. 다시 시도해 주세요.");
      setPending(false);
    }
  };

  return (
    <main className={c("adm-login")}>
      <form className={c("adm-login-card")} onSubmit={submit}>
        <h1 className={c("adm-login-wordmark")}>BILLIM</h1>
        <p className={c("t-body adm-login-title")}>관리자 로그인</p>
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <label className={c("t-label adm-login-field")}>
          아이디
          <input
            type="text"
            className={c("adm-login-input t-body")}
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
            autoComplete="username"
            autoFocus
            required
          />
        </label>
        <label className={c("t-label adm-login-field")}>
          비밀번호
          <input
            type="password"
            className={c("adm-login-input t-body")}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <button type="submit" className={c("adm-login-submit t-label")} disabled={pending}>
          {pending ? "확인 중…" : "로그인"}
        </button>
      </form>
    </main>
  );
}
