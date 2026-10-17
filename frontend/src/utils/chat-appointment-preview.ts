import type { ApiAppointmentSlot } from "../types/appointment-api.ts";
import type { ApiChatRoom } from "../types/chat-api.ts";

const BATCH_SIZE = 4;

/** 여러 거래의 확정 약속 중 가장 가까운 미래 일정을 목록에 표시한다. */
export function chatAppointmentPreview(slots: ApiAppointmentSlot[], now = Date.now()): string | undefined {
  const next = slots.flatMap((slot) => slot.confirmedProposal ? [slot.confirmedProposal] : [])
    .filter((proposal) => Date.parse(proposal.scheduledAt) >= now)
    .sort((a, b) => Date.parse(a.scheduledAt) - Date.parse(b.scheduledAt))[0];
  if (!next) return undefined;
  const date = new Date(next.scheduledAt);
  const label = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul", month: "numeric", day: "numeric", weekday: "short",
    hour: "numeric", minute: "2-digit", hour12: true,
  }).format(date);
  return `${label} ${next.kind === "PICKUP" ? "전달" : "반납"}`;
}

/** 보이는 채팅 목록의 거래만 조회하며, 실패한 거래는 다른 방의 표시를 막지 않는다. */
export async function loadChatAppointmentPreviews(
  rooms: ApiChatRoom[],
  getAppointments: (rentalId: string) => Promise<ApiAppointmentSlot[]>,
  isActive: () => boolean = () => true,
): Promise<Record<string, string>> {
  const rentalIds = [...new Set(rooms.flatMap((room) => room.rentals
    .filter((rental) => rental.status === "APPROVED" || rental.status === "ACTIVE")
    .map((rental) => rental.id)))];
  const slotsByRental = new Map<string, ApiAppointmentSlot[]>();
  for (let index = 0; index < rentalIds.length && isActive(); index += BATCH_SIZE) {
    const batch = rentalIds.slice(index, index + BATCH_SIZE);
    const results = await Promise.allSettled(batch.map(getAppointments));
    results.forEach((result, offset) => {
      if (result.status === "fulfilled") slotsByRental.set(batch[offset], result.value);
    });
  }
  const previews: Record<string, string> = {};
  for (const room of rooms) {
    const slots = room.rentals.flatMap((rental) => slotsByRental.get(rental.id) ?? []);
    const preview = chatAppointmentPreview(slots);
    if (preview) previews[room.id] = preview;
  }
  return previews;
}
