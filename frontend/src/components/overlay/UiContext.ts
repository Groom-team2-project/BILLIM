import { createContext, useContext } from "react";

export type ConfirmOptions = {
  title: string;
  body?: string;
  /** 확정 버튼 라벨 — 기본 "확인" */
  confirm?: string;
  /** 취소 버튼 라벨 — 기본 "취소" */
  cancel?: string;
  danger?: boolean;
  onConfirm?: () => void;
};

export type ReasonOptions = {
  title: string;
  body?: string;
  placeholder?: string;
  /** 확정 버튼 라벨 — 기본 "확인" */
  confirm?: string;
  danger?: boolean;
  /** true면 사유를 적어야 확정 가능 */
  required?: boolean;
  onConfirm?: (reason: string) => void;
};

export type ReportOptions = {
  /** 신고 대상 이웃 이름 */
  name: string;
  onConfirm?: (reason: string) => void;
};

export type UiApi = {
  confirm: (opts: ConfirmOptions) => void;
  reason: (opts: ReasonOptions) => void;
  report: (opts: ReportOptions) => void;
  toast: (msg: string) => void;
};

export const UiContext = createContext<UiApi | null>(null);

export function useUi(): UiApi {
  const ui = useContext(UiContext);
  if (!ui) throw new Error("UiProvider 바깥에서 useUi 호출");
  return ui;
}
