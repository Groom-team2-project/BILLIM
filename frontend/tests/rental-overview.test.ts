import { test } from "node:test";
import assert from "node:assert/strict";
import { availabilityWindows, summaryCells, toTrust, todayInSeoul } from "../src/utils/rentalOverview.ts";

test("신뢰 지표는 완료·빌려줌·약속 준수 분자와 분모를 서버 그대로 표시", () => {
  assert.deepEqual(toTrust({ completedCount: 17, lentCount: 11, returnPromiseKept: 3, returnPromiseTotal: 5, basis: "확정 약속 기준" }),
    { kept: 3, total: 5, done: 17, lent: 11, since: undefined });
  assert.equal(toTrust({ completedCount: 0, lentCount: 0, returnPromiseKept: 0, returnPromiseTotal: 0, basis: "" }).done, 0);
});
test("홈 요약은 빌려준 요청·빌린 승인·빌린 대여 중 집계 사용, 연체 중복 합산 금지", () => {
  const zero = { requested: 0, approved: 0, active: 0, overdue: 0, returned: 0, rejected: 0, canceled: 0, expired: 0 };
  assert.deepEqual(summaryCells({ lent: { ...zero, requested: 9 }, borrowed: { ...zero, approved: 6, active: 4, overdue: 2 }, nextRentals: [] }).map((c) => c.count), [9, 6, 4]);
});
test("예약 조회는 93일을 넘지 않으며 연도·윤년 경계와 마지막 날짜 포함", () => {
  for (const [from, to] of [["2026-12-01", "2027-05-01"], ["2028-02-01", "2028-05-03"]]) {
    const windows = availabilityWindows(from, to);
    assert.equal(windows[0].from, from);
    assert.equal(windows.at(-1)?.to, to);
    windows.forEach((w, i) => {
      const days = (Date.parse(w.to) - Date.parse(w.from)) / 86400000 + 1;
      assert.ok(days >= 1 && days <= 93);
      if (i > 0) assert.equal(Date.parse(w.from) - Date.parse(windows[i - 1].to), 86400000);
    });
  }
  assert.deepEqual(availabilityWindows("2026-01-02", "2026-01-01"), []);
  assert.deepEqual(availabilityWindows("2026-01-01", "2026-01-01"), [{ from: "2026-01-01", to: "2026-01-01" }]);
});

test("기준일은 실행 환경과 무관하게 KST 자정에 전환", () => {
  const before = todayInSeoul(new Date("2026-12-31T14:59:59Z"));
  const after = todayInSeoul(new Date("2026-12-31T15:00:00Z"));
  assert.deepEqual([before.getFullYear(), before.getMonth(), before.getDate()], [2026, 11, 31]);
  assert.deepEqual([after.getFullYear(), after.getMonth(), after.getDate()], [2027, 0, 1]);
});
