import type { ApiChatRental } from "@/types/chat-api";
import type { RentalStatusView } from "@/components/custom/rentals/status";

export function chatRentalStatus(rental: ApiChatRental): RentalStatusView | null {
  if (rental.overdue) return "overdue";
  if (rental.status === "EXPIRED") return null;
  return rental.status.toLowerCase() as RentalStatusView;
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
