import type { ApiChatMessage, ApiChatRental, ApiChatRoom } from "@/types/chat-api";
import type { ApiAppointmentProposal, ApiAppointmentSlot } from "@/types/appointment-api";
import type { ApiItemDetail } from "@/api/items";
import type { RentalStatusView } from "@/components/custom/rentals/status";

export function chatRentalStatus(rental: ApiChatRental): RentalStatusView | null {
  if (rental.overdue) return "overdue";
  if (rental.status === "EXPIRED") return null;
  return rental.status.toLowerCase() as RentalStatusView;
}

export function selectableChatRentals(room: ApiChatRoom): ApiChatRental[] {
  if (room.access.reason !== "EXISTING_RENTAL_ONLY") return room.rentals;
  return room.rentals.filter((rental) => room.access.allowedRentalIds.includes(rental.id));
}

/** 약속은 승인된 대여에서만 가능하므로, 대여 전에는 날짜 선택 화면으로 안내한다. */
export function canOpenRentalRequestFromChat(room: ApiChatRoom, item: ApiItemDetail | null,
  memberId: string | null, today: string): boolean {
  return Boolean(item && memberId && item.id === room.itemId && item.owner.id !== memberId
    && item.visibility === "PUBLIC" && item.availableEndDate >= today
    && room.access.canSend && room.access.reason === "NORMAL"
    && !room.rentals.some((rental) => ["REQUESTED", "APPROVED", "ACTIVE"].includes(rental.status)));
}

export function chatRentalResponse(rental: ApiChatRental | undefined, memberId: string | null) {
  if (!rental || rental.status !== "REQUESTED") return null;
  const isOwner = rental.owner.id === memberId;
  return {
    isOwner,
    canApprove: isOwner && rental.allowedActions.includes("APPROVE"),
    canReject: isOwner && rental.allowedActions.includes("REJECT"),
  };
}

export type ChatTimelineEntry =
  | { kind: "message"; message: ApiChatMessage; index: number }
  | { kind: "rental-request" }
  | { kind: "rental-approved" }
  | { kind: "appointment"; slot: ApiAppointmentSlot; proposal: ApiAppointmentProposal; status: "confirmed" | "pending" };

export function chatActivityLinks(rental: ApiChatRental | undefined, memberId: string | null,
  timeline: ChatTimelineEntry[]): { kind: string; id: string; label: string }[] {
  if (!rental || !["REQUESTED", "APPROVED", "ACTIVE"].includes(rental.status)) return [];
  const links: { kind: string; id: string; label: string }[] = [];
  for (const entry of timeline) {
    if (entry.kind === "rental-request" && rental.status === "REQUESTED") {
      links.push({ kind: "request", id: rental.id,
        label: rental.owner.id === memberId ? "대여 요청 · 응답 필요" : "대여 요청 · 승인 대기" });
    } else if (entry.kind === "rental-approved") {
      links.push({ kind: "approved", id: rental.id, label: "대여 승인 · 약속 보기" });
    } else if (entry.kind === "appointment") {
      const kind = entry.slot.kind === "RETURN" ? "반납" : "전달";
      links.push({ kind: entry.status, id: entry.proposal.id,
        label: `${kind} 약속 · ${entry.status === "confirmed" ? "확정" : "제안"}` });
    }
  }
  return links;
}

function appointmentTimeLabel(scheduledAt: string): string {
  const date = new Date(scheduledAt);
  if (Number.isNaN(date.getTime())) return "";
  const parts = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul", month: "numeric", day: "numeric", weekday: "short",
    hour: "numeric", minute: "2-digit", hour12: true,
  }).formatToParts(date);
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? "";
  return `${part("month")}/${part("day")}(${part("weekday")}) ${part("dayPeriod")} ${part("hour")}:${part("minute")}`;
}

function appointmentEventMatches(message: ApiChatMessage, rentalId: string,
  proposal: ApiAppointmentProposal, status: "confirmed" | "pending"): boolean {
  if (message.rentalId !== rentalId || message.type !== "SYSTEM") return false;
  if (message.system?.eventType !== (status === "confirmed" ? "APPOINTMENT_CONFIRMED" : "APPOINTMENT_PROPOSED")) return false;
  if (message.appointmentProposalId) return message.appointmentProposalId === proposal.id;
  // 이전 서버에서 저장한 시스템 메시지에는 제안 ID가 없으므로 당시 약속 시각으로 연결한다.
  const time = appointmentTimeLabel(proposal.scheduledAt);
  return Boolean(time && message.system.label.includes(time));
}

/** 거래·약속 카드를 사건 옆에 둔다. 사건이 없으면 승인 카드 뒤나 표시 중인 대화 앞에 둔다. */
export function chatTimeline(messages: ApiChatMessage[], rental?: ApiChatRental, showApproval = false,
  slots: ApiAppointmentSlot[] = []): ChatTimelineEntry[] {
  const entries: ChatTimelineEntry[] = messages.map((message, index) => ({ kind: "message", message, index }));
  if (!rental) return entries;

  const requestIndex = entries.findIndex((entry) => entry.kind === "message"
    && entry.message.rentalId === rental.id && entry.message.system?.eventType === "RENTAL_REQUESTED");
  const laterIndex = messages.findIndex((message) => Date.parse(message.createdAt) > Date.parse(rental.createdAt));
  const requestPosition = requestIndex >= 0 ? requestIndex + 1 : laterIndex >= 0 ? laterIndex : entries.length;
  entries.splice(requestPosition, 0, { kind: "rental-request" });

  if (showApproval) {
    const approvalIndex = entries.findIndex((entry) => entry.kind === "message"
      && entry.message.rentalId === rental.id && entry.message.system?.eventType === "RENTAL_APPROVED");
    entries.splice(approvalIndex >= 0 ? approvalIndex + 1 : requestPosition + 1, 0, { kind: "rental-approved" });
  }
  const usedEvents = new Set<string>();
  for (const slot of slots.filter((item) => item.rentalId === rental.id)) {
    for (const status of ["confirmed", "pending"] as const) {
      const proposal = status === "confirmed" ? slot.confirmedProposal : slot.pendingProposal;
      if (!proposal) continue;
      const event = messages.findLast((message) => !usedEvents.has(message.id)
        && appointmentEventMatches(message, rental.id, proposal, status));
      if (event) usedEvents.add(event.id);
      const approvalPosition = entries.findIndex((entry) => entry.kind === "rental-approved");
      const position = event ? entries.findIndex((entry) => entry.kind === "message" && entry.message.id === event.id) + 1
        : approvalPosition >= 0 ? approvalPosition + 1 : 0;
      entries.splice(position, 0, { kind: "appointment", slot, proposal, status });
    }
  }
  return entries;
}

export function chatTimeLabel(value?: string): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const today = new Date();
  if (date.toDateString() === today.toDateString()) {
    return new Intl.DateTimeFormat("ko-KR", { hour: "numeric", minute: "2-digit" }).format(date);
  }
  return new Intl.DateTimeFormat("ko-KR", { month: "numeric", day: "numeric" }).format(date);
}

export function chatDateRange(rental: ApiChatRental): string {
  const format = (value: string) => {
    const [year, month, day] = value.split("-").map(Number);
    const date = new Date(year, month - 1, day);
    if (Number.isNaN(date.getTime())) return value;
    return `${month}/${day}(${"일월화수목금토"[date.getDay()]})`;
  };
  return `${format(rental.startDate)} – ${format(rental.endDate)}`;
}

export function chatBlockedMessage(reason: string): string {
  switch (reason) {
    case "BLOCKED": return "차단된 이웃과는 대화할 수 없어요.";
    case "RESTRICTED": return "이용 제한 중에는 메시지를 보낼 수 없어요.";
    case "COMMUNITY_CHANGED": return "이 동네의 유효한 인증이 없어요. 내 동네 설정에서 확인해 주세요.";
    case "ITEM_UNAVAILABLE": return "이 물건에 새 메시지를 보낼 수 없어요.";
    default: return "현재 메시지를 보낼 수 없어요.";
  }
}

export function isMoneyRequest(body?: string): boolean {
  return !!body && /보증금|계좌|입금|송금|결제|돈\s*보내/.test(body);
}
