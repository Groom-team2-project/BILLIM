import type { ApiChatRental } from "@/types/chat-api";

/** C_029·C_031 응답 — 상태와 식별자는 서버 계약 유지 */
export type ApiRental = ApiChatRental;
export type RentalRole = "BORROWER" | "OWNER";
export type ApiRentalQuery = { status?: ApiRental["status"]; overdueOnly?: boolean; size?: number };
export type ApiRentalCounts = {
  requested: number; approved: number; active: number; overdue: number;
  returned: number; rejected: number; canceled: number; expired: number;
};
export type ApiMyRentalSummary = {
  borrowed: ApiRentalCounts; lent: ApiRentalCounts; nextRentals: ApiRental[];
};
export type ApiRentalPage = {
  items: ApiRental[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
};
export type ApiRentalDetail = {
  rental: ApiRental;
  reason?: string;
  chatRoomId?: string;
  appointments?: {
    kind: string;
    confirmedProposal?: { scheduledAt: string; place: { name: string } };
  }[];
};
