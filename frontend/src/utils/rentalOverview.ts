import type { Trust } from "@/types/item";
import type { ApiRentalSummary } from "@/types/rental-api";
import type { ApiTrustMetrics } from "@/types/member-api";

/** 서버 집계값만 표시 모델로 변환 */
export function toTrust(metrics: ApiTrustMetrics, since?: string): Trust {
  return { kept: metrics.returnPromiseKept, total: metrics.returnPromiseTotal,
    done: metrics.completedCount, lent: metrics.lentCount, since };
}

export function summaryCells(summary: ApiRentalSummary) {
  return [
    { label: "요청 대기", count: summary.lent.requested, note: "빌려준 물건", status: "requested" as const },
    { label: "승인 완료", count: summary.borrowed.approved, note: "빌린 물건", status: "approved" as const },
    { label: "대여 중", count: summary.borrowed.active, note: "빌린 물건", status: "active" as const },
  ];
}

/** 조회 범위는 양끝 포함 93일 이하로 분할 */
export function availabilityWindows(from: string, to: string): { from: string; to: string }[] {
  const windows = [];
  let cursor = from;
  while (cursor <= to) {
    const day = new Date(`${cursor}T00:00:00Z`);
    day.setUTCDate(day.getUTCDate() + 92);
    const end = day.toISOString().slice(0, 10) < to ? day.toISOString().slice(0, 10) : to;
    windows.push({ from: cursor, to: end });
    const next = new Date(`${end}T00:00:00Z`);
    next.setUTCDate(next.getUTCDate() + 1);
    cursor = next.toISOString().slice(0, 10);
  }
  return windows;
}

/** 서버의 날짜 정책과 동일한 KST 달력 기준일 */
export function todayInSeoul(now = new Date()): Date {
  const value = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}
