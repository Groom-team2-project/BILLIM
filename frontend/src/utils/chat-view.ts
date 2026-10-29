import type { ApiChatMessage, ApiChatRental, ApiChatRoom } from "@/types/chat-api";
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
  | { kind: "rental-approved" };

/** 거래 카드를 발생한 사건 옆에 둔다. 사건이 아직 없으면 요청 시각으로 위치를 고정한다. */
export function chatTimeline(messages: ApiChatMessage[], rental?: ApiChatRental, showApproval = false): ChatTimelineEntry[] {
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
    case "COMMUNITY_CHANGED": return "동네가 달라져 새 메시지를 보낼 수 없어요.";
    case "ITEM_UNAVAILABLE": return "이 물건에 새 메시지를 보낼 수 없어요.";
    default: return "현재 메시지를 보낼 수 없어요.";
  }
}

export function isMoneyRequest(body?: string): boolean {
  return !!body && /보증금|계좌|입금|송금|결제|돈\s*보내/.test(body);
}
