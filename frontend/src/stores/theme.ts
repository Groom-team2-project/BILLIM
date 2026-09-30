import { useSyncExternalStore } from "react";

export type ThemeName = "white" | "cream" | "blue" | "red" | "green";

export type ThemeState = {
  theme: ThemeName;
  dark: boolean;
};

const STORAGE_KEY = "billim-theme";
const THEME_CLASSES = ["theme-white", "theme-cream", "theme-blue", "theme-red", "theme-green"];

function load(): ThemeState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw) as ThemeState;
  } catch {
    // 저장값 없음 · 접근 불가 — 기본값 사용
  }
  const systemDark = typeof matchMedia !== "undefined" && matchMedia("(prefers-color-scheme: dark)").matches;
  return { theme: "cream", dark: systemDark };
}

let state: ThemeState = load();
const subs = new Set<() => void>();

/** 다크는 팔레트 무관 단일 톤 — 테마 클래스는 라이트에서만 적용 (프로토타입 동일) */
function apply() {
  const el = document.documentElement;
  el.classList.remove(...THEME_CLASSES);
  if (!state.dark && state.theme !== "cream") el.classList.add(`theme-${state.theme}`);
  el.dataset.theme = state.dark ? "dark" : "light";
}

export function setTheme(patch: Partial<ThemeState>) {
  state = { ...state, ...patch };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // 저장 실패는 무시 — 현재 세션에만 적용
  }
  apply();
  for (const f of subs) f();
}

function subscribe(f: () => void) {
  subs.add(f);
  return () => {
    subs.delete(f);
  };
}

export function useTheme(): ThemeState {
  return useSyncExternalStore(subscribe, () => state);
}

apply();
