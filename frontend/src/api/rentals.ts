import { availabilityWindows } from "@/utils/rentalOverview";
import { api } from "@/api/client";
import type { ApiRentalDetail, ApiRentalPage, RentalRole } from "@/types/rental-api";

export function getRentals(role: RentalRole, page = 0): Promise<ApiRentalPage> {
  return api("/rentals", { query: { role, page, size: 50 } });
}

export function getRental(rentalId: string): Promise<ApiRentalDetail> {
  return api(`/rentals/${encodeURIComponent(rentalId)}`);
}

export type RentalAction = "approve" | "reject" | "cancel" | "handover" | "return";

export function commandRental(id: string, action: RentalAction, expectedVersion: number, reason?: string): Promise<ApiRentalDetail> {
  return api(`/rentals/${encodeURIComponent(id)}/${action}`, {
    method: "POST",
    json: { expectedVersion, ...(reason === undefined ? {} : { reason }) },
  });
}

/** C_028 요청 생성 — 응답 유실 재시도 시 같은 키 유지 */
export function createRental(body: { itemId: string; startDate: string; endDate: string }, idempotencyKey: string): Promise<ApiRentalDetail> {
  return api("/rentals", { method: "POST", json: body, idempotencyKey });
}

export function getAvailability(itemId: string, from: string, to: string): Promise<{
  occupiedRanges: { startDate: string; endDate: string }[];
  overdueBlocked: boolean;
}> {
  return api(`/items/${encodeURIComponent(itemId)}/availability`, { query: { from, to } });
}

export const getRentalSummary = () => api<import("@/types/rental-api").ApiRentalSummary>("/rentals/summary");

/** 전체 가능 기간을 API의 최대 조회 범위에 맞춰 조회 */
export async function getAvailabilityRange(itemId: string, from: string, to: string) {
  const occupiedRanges: { startDate: string; endDate: string }[] = [];
  let overdueBlocked = false;
  for (const window of availabilityWindows(from, to)) {
    const result = await getAvailability(itemId, window.from, window.to);
    occupiedRanges.push(...result.occupiedRanges);
    overdueBlocked ||= result.overdueBlocked;
  }
  return { occupiedRanges, overdueBlocked };
}
