import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { resetCsrf } from "@/api/client";
import { login } from "@/stores/session";

/** 카카오 로그인 콜백 착지 지점 */
export function AuthCompletePage() {
  const navigate = useNavigate();

  useEffect(() => {
    // 로그인으로 세션 변경 — 이전 세션의 CSRF 토큰 무효
    resetCsrf();
    // 목 세션 설정
    login();
    navigate("/", { replace: true });
  }, [navigate]);

  return <p className="t-body">로그인 중…</p>;
}
