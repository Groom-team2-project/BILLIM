import type { ApiMyRentalSummary } from "@/api/home";
import type { ApiRental } from "@/types/rental-api";
import type { RentalStatusView } from "@/components/custom/rentals/status";

const DOW = ["일", "월", "화", "수", "목", "금", "토"];

// "2026-10-24" → "10/24(토)"
function monthDay(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return `${m}/${d}(${DOW[new Date(y, m - 1, d).getDay()]})`;
}

export type RentalSummaryCell = { label: string; count: number; note: string; status: RentalStatusView };

/** 홈 '내 대여 현황' 칸: 받은 요청, 전달 전 승인, 진행 중 대여 */
export function toRentalSummaryCells(r: ApiMyRentalSummary): RentalSummaryCell[] {
  const overdue = r.borrowed.overdue + r.lent.overdue;
  return [
    { label: "요청 대기", count: r.lent.requested, note: "빌려준 물건", status: "requested" },
    { label: "승인 완료", count: r.borrowed.approved + r.lent.approved, note: "전달 전", status: "approved" },
    {
      label: "대여 중",
      count: r.borrowed.active + r.lent.active,
      note: overdue > 0 ? `연체 ${overdue}건` : "진행 중",
      status: "active",
    },
  ];
}

export type UpcomingRental = { status: RentalStatusView; title: string; detail: string; dDay: string; late: boolean };

/** 가장 가까운 다음 대여 한 건. 없으면 null */
export function toUpcomingRental(next: ApiRental[]): UpcomingRental | null {
  const r = next[0];
  if (!r) return null;
  const receiving = r.status === "APPROVED";
  const date = monthDay(receiving ? r.startDate : r.endDate);
  const when = receiving ? `${date} 받기` : `${date}까지 반납`;
  return {
    status: r.overdue ? "overdue" : (r.status.toLowerCase() as RentalStatusView),
    title: r.itemTitle,
    detail: r.placeName ? `${when} · ${r.placeName}` : when,
    dDay: r.dDay === 0 ? "D-Day" : r.dDay > 0 ? `D-${r.dDay}` : `D+${-r.dDay}`,
    late: r.overdue || r.dDay < 0,
  };
}
