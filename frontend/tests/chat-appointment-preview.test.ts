import { test } from "node:test";
import assert from "node:assert/strict";
import type { ApiAppointmentSlot } from "../src/types/appointment-api.ts";
import type { ApiChatRental, ApiChatRoom } from "../src/types/chat-api.ts";
import { chatAppointmentPreview, loadChatAppointmentPreviews } from "../src/utils/chat-appointment-preview.ts";

function slot(id: string, scheduledAt: string, kind: "PICKUP" | "RETURN"): ApiAppointmentSlot {
  return {
    id, rentalId: "1", kind, version: 1, allowedActions: [],
    confirmedProposal: {
      id, slotId: id, proposerId: "2", kind, scheduledAt, status: "CONFIRMED",
      place: { id: "3", communityId: "4", name: "관리동 택배함" },
    },
  };
}

test("채팅 목록에는 가장 가까운 미래의 확정 약속만 표시한다", () => {
  const now = Date.parse("2026-10-17T00:00:00Z");
  const preview = chatAppointmentPreview([
    slot("past", "2026-10-16T01:00:00Z", "PICKUP"),
    slot("return", "2026-10-21T01:00:00Z", "RETURN"),
    slot("pickup", "2026-10-20T01:00:00Z", "PICKUP"),
  ], now);

  assert.match(preview ?? "", /10.*20.*전달/);
  assert.equal(chatAppointmentPreview([slot("past", "2026-10-16T01:00:00Z", "PICKUP")], now), undefined);
});

test("대기 중인 제안만 있으면 확정 약속처럼 표시하지 않는다", () => {
  const pending = slot("pending", "2026-10-20T01:00:00Z", "PICKUP");
  assert.equal(chatAppointmentPreview([{ ...pending, confirmedProposal: undefined,
    pendingProposal: { ...pending.confirmedProposal!, status: "PROPOSED" } }],
  Date.parse("2026-10-17T00:00:00Z")), undefined);
});

test("한 거래의 약속 조회 실패가 다른 채팅방의 약속 표시를 막지 않는다", async () => {
  const member = { id: "2", displayName: "정우", joinedAt: "2026-10-01T00:00:00Z" };
  const rental = (id: string): ApiChatRental => ({
    id, itemId: "10", itemTitle: "전동드릴", owner: member, borrower: member,
    startDate: "2030-10-20", endDate: "2030-10-21", status: "APPROVED",
    overdue: false, dDay: 1, placeName: "관리동", version: 1,
    allowedActions: [], createdAt: "2026-10-01T00:00:00Z",
  });
  const room = (id: string, linked: ApiChatRental): ApiChatRoom => ({
    id, itemId: "10", communityId: "20", itemTitle: "전동드릴", otherMember: member,
    unreadCount: 0, rentals: [linked],
    access: { canSend: true, allowedRentalIds: [linked.id], reason: "NORMAL" },
  });
  const called: string[] = [];
  const previews = await loadChatAppointmentPreviews(
    [room("a", rental("1")), room("b", rental("2"))],
    async (id) => {
      called.push(id);
      if (id === "1") throw new Error("일시적인 조회 실패");
      return [slot("confirmed", "2030-10-20T01:00:00Z", "PICKUP")];
    },
  );

  assert.deepEqual(called, ["1", "2"]);
  assert.equal(previews.a, undefined);
  assert.match(previews.b, /전달/);
});
