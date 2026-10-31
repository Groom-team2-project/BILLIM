import { test } from "node:test";
import assert from "node:assert/strict";
import { includeRental, matchesRental, rentalTab, toRental } from "../src/utils/rentalView.ts";
import { fmtDate, rangeDays } from "../src/utils/date.ts";

const rental = {
  id: "9007199254740993", itemId: "500", itemTitle: "거래 당시 제목",
  owner: { id: "1", displayName: "소유자", joinedAt: "2026-01-01" },
  borrower: { id: "2", displayName: "대여자", joinedAt: "2026-01-01" },
  startDate: "2027-12-31", endDate: "2028-01-02", status: "ACTIVE" as const,
  overdue: true, dDay: -1, placeName: "거래 당시 장소", version: 3,
  allowedActions: ["RETURN"], createdAt: "2026-10-01T00:00:00Z",
};

test("실제 거래 식별자·스냅샷·상대방·허용 행동 유지 및 연체 파생 표시", () => {
  const view = toRental(rental);
  assert.equal(view.id, rental.id);
  assert.equal(view.itemId, "500");
  assert.equal(view.title, "거래 당시 제목");
  assert.equal(view.place, "거래 당시 장소");
  assert.equal(view.owner, "소유자");
  assert.equal(view.borrower, "대여자");
  assert.equal(view.status, "overdue");
  assert.equal(view.dday, "반납 1일 지남");
  assert.deepEqual(view.allowedActions, ["RETURN"]);
  assert.ok(matchesRental(view, "overdue"));
  assert.ok(!matchesRental(view, "done"));
});

test("요청 만료 및 종료 상태 필터, 연체 플래그는 ACTIVE에만 적용", () => {
  for (const status of ["EXPIRED", "RETURNED", "REJECTED", "CANCELED"] as const) {
    const view = toRental({ ...rental, status });
    assert.equal(view.status, status.toLowerCase());
    assert.ok(matchesRental(view, "done"));
    assert.equal(view.dday, undefined);
  }
});

test("연체 필터는 연체 ACTIVE만 표시하고 다른 상태는 제외", () => {
  const entries = [
    rental,
    { ...rental, id: "2", overdue: false },
    { ...rental, id: "3", status: "RETURNED" as const },
    { ...rental, id: "4", status: "APPROVED" as const },
  ].map((entry) => toRental(entry));
  assert.deepEqual(entries.filter((entry) => matchesRental(entry, "overdue")).map((entry) => entry.id), [rental.id]);
  assert.deepEqual(entries.filter((entry) => matchesRental(entry, "active")).map((entry) => entry.id), ["2"]);
  assert.equal(entries.filter((entry) => matchesRental(entry, "all")).length, 4);
});

test("알림 거래 당사자에 맞는 탭 선택", () => {
  assert.equal(rentalTab(rental, "1"), "lent");
  assert.equal(rentalTab(rental, "2"), "borrowed");
});

test("현재 페이지 밖 알림 거래 추가 및 기존 거래 중복 제거", () => {
  const other = { ...rental, id: "10" };
  assert.deepEqual(includeRental([other], rental), [rental, other]);
  assert.deepEqual(includeRental([other, rental], rental), [rental, other]);
  assert.deepEqual(includeRental([other]), [other]);
});

test("연도 경계 대여 기간과 요일 계산", () => {
  const view = toRental(rental);
  assert.equal(fmtDate(view.start), "12/31(금)");
  assert.equal(fmtDate(view.end), "1/2(일)");
  assert.equal(rangeDays(view.start, view.end), 3);
  assert.equal(fmtDate([9, 26]), "9/26(토)");
});

test("상세 응답의 사유·채팅방·확정 전달 약속 표시", () => {
  const approved = { ...rental, status: "APPROVED" as const, overdue: false };
  const view = toRental(approved, {
    rental: approved, reason: "사유", chatRoomId: "99",
    appointments: [{ kind: "PICKUP", confirmedProposal: { scheduledAt: "2027-12-31T01:00:00Z", place: { name: "정문" } } }],
  });
  assert.equal(view.reason, "사유");
  assert.equal(view.chatRoomId, "99");
  assert.match(view.appt!, /정문/);
  assert.match(view.appt!, /10:00/);
});

test("재조회한 상세의 최신 상태·허용 행동·반납 약속을 목록보다 우선", () => {
  const old = { ...rental, status: "APPROVED" as const, overdue: false, version: 1, allowedActions: ["HANDOVER"] };
  const view = toRental(old, {
    rental, chatRoomId: "99",
    appointments: [{ kind: "RETURN", confirmedProposal: { scheduledAt: "2028-01-02T02:00:00Z", place: { name: "반납 장소" } } }],
  });
  assert.equal(view.status, "overdue");
  assert.deepEqual(view.allowedActions, ["RETURN"]);
  assert.equal(view.chatRoomId, "99");
  assert.match(view.appt!, /반납 장소/);
  assert.match(view.appt!, /11:00/);
});
