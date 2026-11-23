import assert from "node:assert/strict";
import test from "node:test";
import { toRentalSummaryCells, toUpcomingRental } from "../src/utils/homeView.ts";

const counts = (o: Partial<Record<string, number>> = {}) => ({
  requested: 0, approved: 0, active: 0, overdue: 0, returned: 0, rejected: 0, canceled: 0, expired: 0, ...o,
});

const rental = (o: Record<string, unknown> = {}) =>
  ({
    id: "1", itemId: "7", itemTitle: "전동드릴", owner: {}, borrower: {},
    startDate: "2026-10-24", endDate: "2026-10-26", status: "APPROVED",
    overdue: false, dDay: 2, placeName: "강남역 11번 출구 앞", version: 0, allowedActions: [], createdAt: "",
    ...o,
  }) as never;

test("내 대여 현황: 받은 요청은 빌려준 쪽, 승인·대여 중은 양쪽 합", () => {
  const cells = toRentalSummaryCells({
    borrowed: counts({ approved: 1, active: 2 }),
    lent: counts({ requested: 3, approved: 1, active: 1 }),
    nextRentals: [],
  });
  assert.deepEqual(cells.map((c) => [c.label, c.count]), [["요청 대기", 3], ["승인 완료", 2], ["대여 중", 3]]);
  assert.equal(cells[2].note, "진행 중");
});

test("내 대여 현황: 연체가 있으면 대여 중 칸에 연체 건수를 알린다", () => {
  const cells = toRentalSummaryCells({ borrowed: counts({ overdue: 1 }), lent: counts({ overdue: 1 }), nextRentals: [] });
  assert.equal(cells[2].note, "연체 2건");
});

test("다음 대여: 없으면 null", () => {
  assert.equal(toUpcomingRental([]), null);
});

test("다음 대여: 승인 상태는 시작일에 받기, D-day 표시", () => {
  const u = toUpcomingRental([rental()]);
  assert.ok(u);
  assert.equal(u.status, "approved");
  assert.equal(u.detail, "10/24(토) 받기 · 강남역 11번 출구 앞");
  assert.equal(u.dDay, "D-2");
  assert.equal(u.late, false);
});

test("다음 대여: 대여 중은 종료일까지 반납, 당일은 D-Day", () => {
  const u = toUpcomingRental([rental({ status: "ACTIVE", dDay: 0 })]);
  assert.ok(u);
  assert.equal(u.status, "active");
  assert.equal(u.detail, "10/26(월)까지 반납 · 강남역 11번 출구 앞");
  assert.equal(u.dDay, "D-Day");
});

test("다음 대여: 연체면 연체 배지와 D+ 표시", () => {
  const u = toUpcomingRental([rental({ status: "ACTIVE", overdue: true, dDay: -1 })]);
  assert.ok(u);
  assert.equal(u.status, "overdue");
  assert.equal(u.dDay, "D+1");
  assert.equal(u.late, true);
});
