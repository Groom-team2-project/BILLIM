import { api } from "@/api/client";
import type { ApiCommunity } from "@/api/communities";
import type { ApiCategory, ApiItemSummary } from "@/api/items";
import type { ApiRental } from "@/types/rental-api";

/** 상태별 대여 건수 (API 명세 RentalCounts) */
export type ApiRentalCounts = {
  requested: number;
  approved: number;
  active: number;
  overdue: number;
  returned: number;
  rejected: number;
  canceled: number;
  expired: number;
};

/** 내 대여 요약 (API 명세 MyRentalSummary) */
export type ApiMyRentalSummary = {
  borrowed: ApiRentalCounts;
  lent: ApiRentalCounts;
  nextRentals: ApiRental[];
};

/** 홈 화면 조립 응답 (API 명세 Home) */
export type ApiHome = {
  community: ApiCommunity;
  itemCount: number;
  categories: ApiCategory[];
  latestItems: ApiItemSummary[];
  myRentals: ApiMyRentalSummary;
  unreadNotificationCount: number;
  unreadChatCount: number;
};

/** B_027 홈 화면 조립 — 동네·최신 물건·물건 수·내 대여 요약·알림 수를 한 번에 */
export const getHome = () => api<ApiHome>("/home");
