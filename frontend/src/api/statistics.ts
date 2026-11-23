import { api } from "@/api/client";

/**
 * 동네 운영 통계 API (E_069). 필드명은 API 명세서 그대로.
 *
 * 집계 정의는 도메인 가이드 결정 '운영 통계'에 있다. 누적과 기간이 섞여 있으니
 * 화면 문구를 바꿀 때 어느 쪽인지 확인한다.
 */

export type ApiCategory = {
  id: string;
  code: string;
  name: string;
  sortOrder: number;
};

export type ApiDailyCount = { date: string; count: number };

export type ApiCategoryCount = { category: ApiCategory; count: number };

export type ApiPopularItem = { itemId: string; title: string; returnedCount: number };

export type ApiAdminStats = {
  communityId: string;
  from: string;
  to: string;
  /** 누적 */
  registeredItems: number;
  /** 누적 · APPROVED·ACTIVE */
  activeRentals: number;
  newItems: number;
  newMembers: number;
  requestCount: number;
  successfulRequestCount: number;
  /** 요청 대비 성사 비율. 진행 중인 요청도 분모에 들어간다 */
  successRate: number;
  /** 누적 */
  pendingReports: number;
  dailyRentalCounts: ApiDailyCount[];
  categoryCounts: ApiCategoryCount[];
  popularItems: ApiPopularItem[];
};

/** E_069 동네 운영 통계. from/to는 KST 달력 날짜(YYYY-MM-DD) 양끝 포함, 최대 31일 */
export function getAdminStatistics(
  communityId: string,
  from: string,
  to: string,
): Promise<ApiAdminStats> {
  return api<ApiAdminStats>("/admin/statistics", { query: { communityId, from, to } });
}
