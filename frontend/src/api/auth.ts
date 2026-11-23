import { api } from "@/api/client";

/**
 * 관리자 전용 로그인. 소셜 계정 없이 서버 환경 변수 자격증명으로 ADMIN 세션 획득.
 * 성공 시 세션이 회전되므로 호출 쪽에서 resetCsrf 후 /members/me로 상태 재구성.
 */
export function adminLogin(loginId: string, password: string): Promise<void> {
  return api<void>("/auth/admin/login", { method: "POST", json: { loginId, password } });
}

/** 현재 기기 외 같은 회원의 세션 전부 폐기. 현재 세션·CSRF 토큰은 유지 */
export function logoutOthers(): Promise<{ revokedCount: number }> {
  return api<{ revokedCount: number }>("/auth/logout-others", { method: "POST" });
}
