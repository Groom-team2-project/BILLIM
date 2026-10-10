import { useSyncExternalStore } from "react";
import { api, resetCsrf } from "@/api/client";

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

/** 서버 세션을 폐기한 뒤 로컬 상태를 비운다. 실패해도 로컬은 비운다 */
export async function logout() {
  try {
    await api<void>("/auth/logout", { method: "POST" });
  } catch {
    // 이미 만료·폐기된 세션이면 서버 호출이 실패해도 로그아웃으로 처리
  }
  resetCsrf();
  commit({ loggedIn: false, admin: false });
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
