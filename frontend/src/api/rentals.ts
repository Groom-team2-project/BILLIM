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
