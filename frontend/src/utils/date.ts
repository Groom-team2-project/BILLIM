/** 날짜 유틸 — API 날짜는 세 번째 값으로 연도 전달, 목데이터는 2026년 기본값 */

const DOW = ["일", "월", "화", "수", "목", "금", "토"];

export type MonthDay = [number, number];

export function fmtDate([m, d, year = 2026]: MonthDay | number[]) {
  const t = new Date(year, m - 1, d);
  return `${m}/${d}(${DOW[t.getDay()]})`;
}

export function md(d: Date): MonthDay {
  return [d.getMonth() + 1, d.getDate()];
}

export function rangeDays(start: number[], end: number[]) {
  const a = new Date(start[2] ?? 2026, start[0] - 1, start[1]);
  const b = new Date(end[2] ?? 2026, end[0] - 1, end[1]);
  return Math.round((b.getTime() - a.getTime()) / 864e5) + 1;
}
