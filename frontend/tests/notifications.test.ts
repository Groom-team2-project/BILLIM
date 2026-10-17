import { test } from "node:test";
import assert from "node:assert/strict";
import type { ApiNotification } from "../src/types/notification.ts";
import {
  appendNotificationPage,
  applyNotificationRead,
  observedThroughId,
} from "../src/utils/notification-state.ts";
import { notificationDestination, notificationTargetPath } from "../src/utils/notification-view.ts";

function notification(id: string, readAt?: string): ApiNotification {
  return {
    id,
    type: "RENTAL_REQUESTED",
    title: `알림 ${id}`,
    targetType: "RENTAL",
    targetId: "7",
    readAt,
    createdAt: "2026-10-13T00:00:00Z",
  };
}

test("다음 페이지에 중복 ID가 있어도 기존 읽음 상태와 순서를 유지한다", () => {
  const current = [notification("30", "2026-10-13T01:00:00Z"), notification("20")];
  const merged = appendNotificationPage(current, [notification("20", "2026-10-13T01:00:00Z"), notification("10")]);

  assert.deepEqual(merged.map((item) => item.id), ["30", "20", "10"]);
  assert.equal(merged[1].readAt, undefined);
  assert.deepEqual(current.map((item) => item.id), ["30", "20"]);
});

test("개별 읽음은 처음 읽을 때만 안 읽은 수를 줄인다", () => {
  const first = applyNotificationRead(
    [notification("30"), notification("20")],
    2,
    notification("30", "2026-10-13T01:00:00Z"),
  );
  assert.equal(first.unreadCount, 1);
  assert.equal(first.items[0].readAt, "2026-10-13T01:00:00Z");

  const repeated = applyNotificationRead(first.items, first.unreadCount, first.items[0]);
  assert.equal(repeated.unreadCount, 1);
});

test("모두 읽음 기준은 관측한 최신 ID이고 이후 새 알림 ID는 포함하지 않는다", () => {
  const observed = [notification("30"), notification("20")];
  assert.equal(observedThroughId(observed), "30");
  assert.equal(observedThroughId([]), undefined);
  assert.equal(BigInt("31") > BigInt(observedThroughId(observed)!), true);
});

test("대여 알림은 대상 ID를 보존하고 다른 알림은 해당 화면으로 이동한다", () => {
  assert.equal(notificationTargetPath(notification("1")), "/rentals?rentalId=7");
  assert.equal(notificationTargetPath({ ...notification("1"), targetId: "7&state=conflict" }), "/rentals?rentalId=7%26state%3Dconflict");
  assert.equal(notificationTargetPath({ ...notification("2"), targetType: "REPORT" }), "/reports");
  assert.equal(notificationTargetPath({ ...notification("3"), targetType: "MEMBER_SELF" }), "/profile");
  assert.equal(notificationTargetPath({ ...notification("4"), type: "APPOINTMENT_CONFIRMED" }), "/chat");
  assert.equal(notificationTargetPath({ ...notification("5"), type: "RENTAL_APPROVED" }), "/chat");
});

test("승인·약속 알림은 거래의 채팅방으로 이동하고 연결이 없으면 이동하지 않는다", async () => {
  const approved = { ...notification("5"), type: "RENTAL_APPROVED" };
  const confirmed = { ...notification("6"), type: "APPOINTMENT_CONFIRMED" };
  assert.equal(await notificationDestination(approved, async (id) => {
    assert.equal(id, "7");
    return { chatRoomId: "42" };
  }), "/chat/42");
  assert.equal(await notificationDestination(confirmed, async () => ({})), undefined);
  assert.equal(await notificationDestination(notification("1"), async () => {
    throw new Error("요청 알림은 채팅방을 조회하지 않는다");
  }), "/rentals?rentalId=7");
});
