import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { resetCsrf } from "@/api/client";
import { getMyProfile } from "@/api/members";
import { signIn } from "@/stores/session";

/** 카카오 로그인 콜백 착지 지점 */
export function AuthCompletePage() {
  const navigate = useNavigate();

  useEffect(() => {
    let canceled = false;

    // 로그인으로 세션 변경 — 이전 세션의 CSRF 토큰 무효
    resetCsrf();

    getMyProfile()
      .then((profile) => {
        if (canceled) return;
        signIn(profile.role);
        // 활성 동네가 없으면 동네 찾기부터. 판정은 서버의 onboardingRequired
        navigate(profile.onboardingRequired ? "/join" : "/", { replace: true });
      })
      .catch(() => {
        if (!canceled) navigate("/login?error=PROFILE_LOAD_FAILED", { replace: true });
      });

    return () => {
      canceled = true;
    };
  }, [navigate]);

  return <p className="t-body">로그인 중…</p>;
}
