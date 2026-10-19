import { test } from "node:test";
import assert from "node:assert/strict";
import { availabilityWindows, todayInSeoul } from "../src/utils/rentalAvailability.ts";

test("예약 조회는 최대 93일씩 중복·누락 없이 연도·윤년 경계 포함", () => {
  for (const [from, to] of [["2026-12-01", "2027-05-01"], ["2028-02-01", "2028-05-03"]]) {
    const windows = availabilityWindows(from, to);
    assert.equal(windows[0].from, from);
    assert.equal(windows.at(-1)?.to, to);
    windows.forEach((window, i) => {
      const days = (Date.parse(window.to) - Date.parse(window.from)) / 86400000 + 1;
      assert.ok(days >= 1 && days <= 93);
      if (i > 0) assert.equal(Date.parse(window.from) - Date.parse(windows[i - 1].to), 86400000);
    });
  }
});
test("하루 기간은 한 번 조회하고 지난 가능 기간은 조회 생략", () => {
  assert.deepEqual(availabilityWindows("2026-01-02", "2026-01-01"), []);
  assert.deepEqual(availabilityWindows("2026-01-01", "2026-01-01"), [{ from: "2026-01-01", to: "2026-01-01" }]);
});
test("달력 기준일은 KST 자정에 연도 전환", () => {
  const before = todayInSeoul(new Date("2026-12-31T14:59:59Z"));
  const after = todayInSeoul(new Date("2026-12-31T15:00:00Z"));
  assert.deepEqual([before.getFullYear(), before.getMonth(), before.getDate()], [2026, 11, 31]);
  assert.deepEqual([after.getFullYear(), after.getMonth(), after.getDate()], [2027, 0, 1]);
});
