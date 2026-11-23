import { api } from "@/api/client";
import type { ApiAppointmentSlot, AppointmentKind } from "@/types/appointment-api";

export function getAppointments(rentalId: string): Promise<ApiAppointmentSlot[]> {
  return api<{ items: ApiAppointmentSlot[] }>(`/rentals/${encodeURIComponent(rentalId)}/appointments`)
    .then((response) => response.items);
}

export function proposeAppointment(
  rentalId: string,
  kind: AppointmentKind,
  body: { expectedVersion: number; scheduledAt: string; placeId: string },
): Promise<ApiAppointmentSlot> {
  return api<ApiAppointmentSlot>(
    `/rentals/${encodeURIComponent(rentalId)}/appointments/${kind}/proposals`,
    { method: "POST", json: body },
  );
}

export function acceptAppointment(proposalId: string, expectedVersion: number): Promise<ApiAppointmentSlot> {
  return api<ApiAppointmentSlot>(`/appointments/${encodeURIComponent(proposalId)}/accept`, {
    method: "POST", json: { expectedVersion },
  });
}

export function withdrawAppointment(proposalId: string, expectedVersion: number): Promise<ApiAppointmentSlot> {
  return api<ApiAppointmentSlot>(`/appointments/${encodeURIComponent(proposalId)}/withdraw`, {
    method: "POST", json: { expectedVersion },
  });
}
