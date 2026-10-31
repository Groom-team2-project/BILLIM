import { api } from "@/api/client";
import type { ApiCommunity } from "@/api/communities";
import type { ApiCategory, ApiItemSummary } from "@/api/items";
import type { ApiMyRentalSummary } from "@/types/rental-api";
export type { ApiRentalCounts, ApiMyRentalSummary } from "@/types/rental-api";

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
