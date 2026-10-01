import { useSearchParams } from "react-router-dom";

/** 화면 상태 — 목데이터 단계에서 `?state=` 로 확인 (API 연동 시 로딩·오류 상태로 대체) */
export type ScreenState = "default" | "loading" | "empty" | "error";

export function useScreenState(): ScreenState {
  const [params] = useSearchParams();
  const s = params.get("state");
  return s === "loading" || s === "empty" || s === "error" ? s : "default";
}
