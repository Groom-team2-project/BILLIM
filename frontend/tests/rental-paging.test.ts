import { test } from "node:test";
import assert from "node:assert/strict";
import { compareRentals, loadRentalPage, rentalCursor, type RentalPageFetcher } from "../src/utils/rentalPaging.ts";
import { rentalFilterCount } from "../src/utils/rentalView.ts";
import type { ApiRental } from "../src/types/rental-api.ts";

const person = { id: "1", displayName: "이웃", joinedAt: "2026-01-01" };
const row = (id: number, status: ApiRental["status"] = "APPROVED"): ApiRental => ({
  id: String(id), itemId: "1", itemTitle: "물건", owner: person, borrower: { ...person, id: "2" },
  status, overdue: false, dDay: 0, startDate: "2026-10-31", endDate: "2026-11-02",
  placeName: "정문", version: 1, allowedActions: [], createdAt: "2026-10-31T00:00:00Z",
});
function source(rows: ApiRental[], calls: unknown[] = []): RentalPageFetcher {
  return async (role, page, query) => {
    calls.push({ role, page, ...query });
    const selected = rows.filter((r) => (!query.status || r.status === query.status) && (!query.overdueOnly || r.overdue));
    const size = query.size!;
    return { items: selected.slice(page * size, (page + 1) * size), page, size, totalElements: selected.length, hasNext: (page + 1) * size < selected.length };
  };
}

test("1,000건에서도 현재 역할의 첫 20건만 요청하고 다음 조회는 명시적으로 진행", async () => {
  const rows = Array.from({ length: 1000 }, (_, i) => row(1000 - i));
  const calls: unknown[] = [];
  const fetch = source(rows, calls);
  const first = await loadRentalPage("BORROWER", "all", rentalCursor("all"), fetch);
  assert.equal(first.items.length, 20);
  assert.equal(first.hasNext, true);
  assert.deepEqual(calls, [{ role: "BORROWER", page: 0, size: 20 }]);
  const second = await loadRentalPage("BORROWER", "all", first.cursor, fetch);
  assert.equal(second.items[0].id, "980");
  assert.equal(new Set([...first.items, ...second.items].map(r => r.id)).size, 40);
});

test("완료 4개 상태의 분포가 달라도 전체 생성 순서와 누락 없는 페이지 병합", async () => {
  const statuses: ApiRental["status"][] = ["RETURNED", "REJECTED", "CANCELED", "EXPIRED"];
  const rows = Array.from({ length: 111 }, (_, i) => row(1000 - i, i < 85 ? "RETURNED" : statuses[i % 4]));
  let cursor = rentalCursor("done");
  const result: ApiRental[] = [];
  let hasNext = true;
  while (hasNext) {
    const page = await loadRentalPage("OWNER", "done", cursor, source(rows));
    result.push(...page.items);
    cursor = page.cursor;
    hasNext = page.hasNext;
  }
  assert.deepEqual(result.map(r => r.id), rows.map(r => r.id));
});

test("대여 중은 연체를 제외해 20건을 채우고 연체는 서버 조건으로 조회", async () => {
  const rows = Array.from({ length: 65 }, (_, i) => ({ ...row(100 - i, "ACTIVE"), overdue: i < 45 }));
  const page = await loadRentalPage("BORROWER", "active", rentalCursor("active"), source(rows));
  assert.equal(page.items.length, 20);
  assert.ok(page.items.every(r => !r.overdue));
  const calls: unknown[] = [];
  const overdue = await loadRentalPage("OWNER", "overdue", rentalCursor("overdue"), source(rows, calls));
  assert.ok(overdue.items.every(r => r.overdue));
  assert.deepEqual(calls, [{ role: "OWNER", page: 0, status: "ACTIVE", overdueOnly: true, size: 20 }]);
});

test("다음 페이지 실패 후 같은 커서로 재시도해 누락·중복 방지", async () => {
  const rows = Array.from({ length: 55 }, (_, i) => row(100 - i, i < 30 ? "RETURNED" : "REJECTED"));
  const first = await loadRentalPage("OWNER", "done", rentalCursor("done"), source(rows));
  const snapshot = JSON.stringify(first.cursor);
  await assert.rejects(loadRentalPage("OWNER", "done", first.cursor, async () => { throw new Error("조회 실패"); }));
  assert.equal(JSON.stringify(first.cursor), snapshot);
  const retry = await loadRentalPage("OWNER", "done", first.cursor, source(rows));
  assert.deepEqual([...first.items, ...retry.items].map(r => r.id), rows.slice(0, 40).map(r => r.id));
});

test("빈 목록은 다음 요청 없이 종료, 같은 시각은 큰 숫자 ID와 마이크로초 순서 유지", async () => {
  const empty = await loadRentalPage("BORROWER", "all", rentalCursor("all"), source([]));
  assert.equal(empty.hasNext, false);
  assert.deepEqual(empty.items, []);
  const a = { ...row(1), id: "9007199254740993", createdAt: "2026-10-31T00:00:00.000001Z" };
  const b = { ...a, id: "9007199254740994" };
  assert.ok(compareRentals(a, b) > 0);
  assert.ok(compareRentals({ ...a, createdAt: "2026-10-31T00:00:00.000002Z" }, b) < 0);
});

test("요약 건수는 전체 거래 기준, active와 overdue를 이중 합산하지 않음", () => {
  const counts = { requested: 10, approved: 20, active: 30, overdue: 5, returned: 15, rejected: 10, canceled: 8, expired: 7 };
  assert.equal(rentalFilterCount(counts, "all"), 100);
  assert.equal(rentalFilterCount(counts, "active"), 25);
  assert.equal(rentalFilterCount(counts, "overdue"), 5);
  assert.equal(rentalFilterCount(counts, "done"), 40);
});
