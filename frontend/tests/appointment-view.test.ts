import { test } from "node:test";
import assert from "node:assert/strict";
import { appointmentDateLabel, appointmentInstant, appointmentView } from "../src/utils/appointment-view.ts";

test("약속 제안 시 서울 시간과 대여 날짜를 UTC 시각으로 보낸다", () => {
  assert.equal(appointmentInstant("2026-10-18", "09:30"), "2026-10-18T00:30:00.000Z");
  assert.equal(appointmentInstant("2026-10-18", "00:30"), "2026-10-17T15:30:00.000Z");
});

test("서버의 UTC 시각을 서울 날짜와 시간으로 표시한다", () => {
  const view = appointmentView({
    id: "1", slotId: "2", proposerId: "3", kind: "PICKUP",
    scheduledAt: "2026-10-17T15:30:00Z",
    place: { id: "4", communityId: "5", name: "관리동 입구" },
    status: "PROPOSED",
  });
  assert.equal(view.kind, "전달");
  assert.equal(view.date, appointmentDateLabel("2026-10-18"));
  assert.match(view.time, /12:30|0:30/);
  assert.equal(view.place, "관리동 입구");
});
