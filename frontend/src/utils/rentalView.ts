import type { ApiRental, ApiRentalDetail, ApiRentalCounts } from "@/types/rental-api";
import type { Rental, RentalStatusView } from "@/types/rental";

export type RentalTab = "borrowed" | "lent";
export type RentalFilter = "all" | "requested" | "approved" | "active" | "overdue" | "done";

export function rentalFilterCount(counts: ApiRentalCounts, filter: RentalFilter) {
  const done = counts.returned + counts.rejected + counts.canceled + counts.expired;
  if (filter === "all") return counts.requested + counts.approved + counts.active + done;
  if (filter === "done") return done;
  if (filter === "active") return counts.active - counts.overdue;
  return counts[filter];
}

export function matchesRental(rental: Rental, filter: RentalFilter) {
  if (filter === "all") return true;
  if (filter === "done") return ["returned", "rejected", "canceled", "expired"].includes(rental.status);
  return rental.status === filter;
}

export function rentalTab(rental: ApiRental, memberId: string): RentalTab {
  return rental.owner.id === memberId ? "lent" : "borrowed";
}

export function toRental(r: ApiRental, detail?: ApiRentalDetail): Rental {
  r = detail?.rental ?? r;
  const date = (value: string): [number, number, number] => {
    const [year, month, day] = value.split("-").map(Number);
    return [month, day, year];
  };
  const status: RentalStatusView = r.status === "ACTIVE" && r.overdue
    ? "overdue" : r.status.toLowerCase() as RentalStatusView;
  const deadline = r.status === "ACTIVE" ? "반납" : "받기";
  const dday = ["REQUESTED", "APPROVED", "ACTIVE"].includes(r.status)
    ? r.dDay < 0 ? `${deadline} ${Math.abs(r.dDay)}일 지남` : `${deadline} D-${r.dDay}` : undefined;
  const appointment = detail?.appointments?.find((slot) => slot.kind === (r.status === "ACTIVE" ? "RETURN" : "PICKUP"))?.confirmedProposal;
  return {
    id: r.id, itemId: r.itemId, title: r.itemTitle, cat: "etc",
    owner: r.owner.displayName, borrower: r.borrower.displayName,
    status, start: date(r.startDate), end: date(r.endDate),
    startDate: r.startDate, endDate: r.endDate, dday, place: r.placeName,
    allowedActions: r.allowedActions, reason: detail?.reason, chatRoomId: detail?.chatRoomId,
    appt: appointment ? `${new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "numeric", day: "numeric", hour: "numeric", minute: "2-digit" }).format(new Date(appointment.scheduledAt))} · ${appointment.place.name}` : undefined,
  };
}

/** 알림 거래가 현재 목록 페이지 밖에 있어도 중복 없이 표시 */
export function includeRental(items: ApiRental[], target?: ApiRental): ApiRental[] {
  return target ? [target, ...items.filter((item) => item.id !== target.id)] : items;
}
