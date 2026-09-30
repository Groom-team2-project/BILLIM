import { useSyncExternalStore } from "react";

/** 목 세션 — API 연동 시 서버 세션 쿠키·권한 조회로 교체 */
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

/** 목 계정은 관리자 — 관리자 메뉴·콘솔 시연용. 실서비스는 서버 권한 검사 (도메인 가이드 '결정: 관리자 진입') */
export function login() {
  commit({ loggedIn: true, admin: true });
}

export function logout() {
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
