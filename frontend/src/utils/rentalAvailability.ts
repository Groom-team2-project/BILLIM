/** 조회 범위는 양끝 포함 최대 93일, 날짜 계산은 UTC로 일광 절약 시간 영향 방지 */
export function availabilityWindows(from: string, to: string): { from: string; to: string }[] {
  const result = [];
  let cursor = from;
  while (cursor <= to) {
    const end = new Date(`${cursor}T00:00:00Z`);
    end.setUTCDate(end.getUTCDate() + 92);
    const last = end.toISOString().slice(0, 10) < to ? end.toISOString().slice(0, 10) : to;
    result.push({ from: cursor, to: last });
    const next = new Date(`${last}T00:00:00Z`);
    next.setUTCDate(next.getUTCDate() + 1);
    cursor = next.toISOString().slice(0, 10);
  }
  return result;
}

/** API와 같은 KST 기준일을 달력용 로컬 Date로 변환 */
export function todayInSeoul(now = new Date()): Date {
  const value = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}
