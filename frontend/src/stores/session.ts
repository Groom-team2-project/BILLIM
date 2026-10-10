import { useSyncExternalStore } from "react";
import { api, ApiError, resetCsrf } from "@/api/client";

/**
 * 화면 표시용 세션 캐시. 실제 인증은 서버의 BILLIM_SESSION 쿠키가 판정한다.
 * 여기 값은 헤더·가드가 쓰는 표시 상태일 뿐이라 신뢰 경계가 아니다.
 */
export type Session = {
  loggedIn: boolean;
  admin: boolean;
};

const STORAGE_KEY = "billim-session";

function load(): Session {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw) as Session;
  } catch {
    // 저장값 없음 · 접근 불가 — 로그아웃 상태
  }
  return { loggedIn: false, admin: false };
}

let state: Session = load();
const subs = new Set<() => void>();

function commit(next: Session) {
  state = next;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // 저장 실패는 무시 — 현재 세션에만 적용
  }
  for (const f of subs) f();
}

/** 로그인 직후 서버가 준 역할로 표시 상태를 맞춘다 */
export function signIn(role: "USER" | "ADMIN") {
  commit({ loggedIn: true, admin: role === "ADMIN" });
}

/**
 * 서버 세션 폐기 후 로컬 표시 상태 정리.
 * 폐기 실패 시 로그인 상태 유지 — 화면만 로그아웃으로 보이고 서버 세션이 살아 있는 상태 방지.
 * 반환값은 로그아웃 확정 여부.
 */
export async function logout(): Promise<boolean> {
  try {
    await api<void>("/auth/logout", { method: "POST" });
  } catch (e) {
    // 401(이미 만료·폐기된 세션)만 로그아웃 성공으로 간주
    if (!(e instanceof ApiError && e.unauthenticated)) return false;
  }
  resetCsrf();
  commit({ loggedIn: false, admin: false });
  return true;
}

function subscribe(f: () => void) {
  subs.add(f);
  return () => {
    subs.delete(f);
  };
}

export function useSession(): Session {
  return useSyncExternalStore(subscribe, () => state);
}
