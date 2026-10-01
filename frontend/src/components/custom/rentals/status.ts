/** 대여 상태의 화면 표시 정보 (DESIGN_SYSTEM.md §5)
 *  서버 저장 상태는 EXPIRED 포함 7종, OVERDUE는 파생 표시 — 명세 확정 기준
 */
export type { RentalStatusView } from "@/data/rentals";
import type { RentalStatusView } from "@/data/rentals";

export const STATUS_UI: Record<RentalStatusView, { label: string; icon: string }> = {
  requested: { label: "요청", icon: "ring" },
  approved: { label: "승인", icon: "check" },
  active: { label: "대여 중", icon: "swap" },
  returned: { label: "반납 완료", icon: "returned" },
  overdue: { label: "연체", icon: "alert" },
  rejected: { label: "거절", icon: "x" },
  canceled: { label: "취소", icon: "dash" },
};
