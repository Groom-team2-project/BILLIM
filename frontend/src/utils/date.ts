/** 목데이터 날짜 유틸 — 2026년 고정. API 연동 시 ISO 날짜 파싱으로 교체 */

const DOW = ["일", "월", "화", "수", "목", "금", "토"];

export type MonthDay = [number, number];

export function fmtDate([m, d]: MonthDay | number[]) {
  const t = new Date(2026, m - 1, d);
  return `${m}/${d}(${DOW[t.getDay()]})`;
}

export function md(d: Date): MonthDay {
  return [d.getMonth() + 1, d.getDate()];
}

export function rangeDays(start: number[], end: number[]) {
  const a = new Date(2026, start[0] - 1, start[1]);
  const b = new Date(2026, end[0] - 1, end[1]);
  return Math.round((b.getTime() - a.getTime()) / 864e5) + 1;
}
