/** 대여 기간 검증 — 양끝 포함 최대 30일, 예약 구간 중간 겹침도 거부 */
export function validateRequestRange(
  range: [Date, Date] | null,
  today: Date,
  from: Date,
  to: Date,
  booked: [Date, Date][],
) {
  const day = (d: Date) => Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
  const days = range ? (day(range[1]) - day(range[0])) / 86400000 + 1 : 0;
  return {
    invalid: !range || days < 1 || days > 30 || day(range[0]) < day(today) || day(range[0]) < day(from) || day(range[1]) > day(to),
    conflict: !!range && booked.some(([a, b]) => day(range[0]) <= day(b) && day(range[1]) >= day(a)),
  };
}
