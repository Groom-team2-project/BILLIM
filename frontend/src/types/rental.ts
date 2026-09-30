/** 대여 도메인 타입 — 상태는 서버 7종 기준 화면 뷰 (정책 및 상태.md) */
import type { CategoryId } from "@/types/item";
import type { MonthDay } from "@/utils/date";

export type RentalStatusView =
  | "requested"
  | "approved"
  | "active"
  | "returned"
  | "overdue"
  | "rejected"
  | "canceled";

export type Rental = {
  id: string;
  title: string;
  cat: CategoryId;
  /** 빌린 목록에서는 소유자, 빌려준 목록에서는 요청자가 상대 */
  owner?: string;
  borrower?: string;
  trustLine?: string;
  status: RentalStatusView;
  start: MonthDay;
  end: MonthDay;
  dday?: string;
  place: string;
  appt?: string;
  reason?: string;
};
