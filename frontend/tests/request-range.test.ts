import { test } from "node:test";
import assert from "node:assert/strict";
import { validateRequestRange } from "../src/utils/requestRange.ts";
const d = (day: number) => new Date(2026, 11, day);
const check = (range: [Date, Date] | null, booked: [Date, Date][] = []) => validateRequestRange(range, d(1), d(1), d(62), booked);
test("대여 기간은 양끝 포함 최대 30일이며 연도 경계도 허용", () => {
  assert.equal(check([d(15), d(44)]).invalid, false);
  assert.equal(check([d(15), d(45)]).invalid, true);
  assert.equal(check([d(1), d(1)]).invalid, false);
});
test("미선택·역전·지난 날짜·가능 기간 밖은 거부", () => {
  for (const range of [null, [d(2), d(1)], [d(0), d(1)], [d(61), d(63)]] as ([Date, Date] | null)[]) assert.equal(check(range).invalid, true);
});
test("예약 구간 사이를 가로지르거나 경계일이 같으면 충돌", () => {
  assert.equal(check([d(2), d(10)], [[d(5), d(6)]]).conflict, true);
  assert.equal(check([d(2), d(5)], [[d(5), d(6)]]).conflict, true);
  assert.equal(check([d(2), d(4)], [[d(5), d(6)]]).conflict, false);
});
