import { test } from "node:test";
import assert from "node:assert/strict";
import type { ApiChatMessage } from "../src/types/chat-api.ts";
import { countNewChatMessages, isChatAtBottom, isChatMessageEndVisible, mergeChatMessages } from "../src/utils/chat-state.ts";
import { isMoneyRequest } from "../src/utils/chat-view.ts";

function message(sequence: number): ApiChatMessage {
  return {
    id: String(sequence), roomId: "1", sequence, type: "TEXT", senderId: "2",
    body: `메시지 ${sequence}`, createdAt: "2026-10-13T00:00:00Z",
  };
}

test("최신·이전 페이지 순서가 달라도 시간순으로 표시하고 중복 전송을 제거한다", () => {
  const latest = mergeChatMessages([], [message(5), message(4), message(3)]);
  const withOlder = mergeChatMessages(latest, [message(2), message(1)]);
  const withPoll = mergeChatMessages(withOlder, [message(5), message(6)]);

  assert.deepEqual(withPoll.map((item) => item.sequence), [1, 2, 3, 4, 5, 6]);
  assert.equal(withPoll.filter((item) => item.id === "5").length, 1);
  assert.deepEqual(latest.map((item) => item.sequence), [3, 4, 5]);
});

test("상대가 보증금·입금을 요구하면 무료 대여 경고를 표시한다", () => {
  assert.equal(isMoneyRequest("보증금으로 만 원만 계좌로 보내주세요"), true);
  assert.equal(isMoneyRequest("물건은 토요일에 택배함에 둘게요"), false);
});

test("하단 판정은 소수점 오차만 허용하고 이전 대화 위치는 유지한다", () => {
  assert.equal(isChatAtBottom({ scrollHeight: 1000, clientHeight: 400, scrollTop: 600 }), true);
  assert.equal(isChatAtBottom({ scrollHeight: 1000, clientHeight: 400, scrollTop: 599.5 }), true);
  assert.equal(isChatAtBottom({ scrollHeight: 1000, clientHeight: 400, scrollTop: 550 }), false);
  assert.equal(isChatAtBottom({ scrollHeight: 200, clientHeight: 400, scrollTop: 0 }), true);
});

test("새 메시지 개수는 상대방의 새 텍스트만 포함하고 재조회 중복은 제외한다", () => {
  const incoming = [message(5), message(6), { ...message(7), senderId: "1" },
    { ...message(8), type: "SYSTEM" as const }, message(9)];
  const merged = mergeChatMessages(mergeChatMessages([], incoming), incoming);
  assert.equal(countNewChatMessages(merged, 5, "2"), 2);
  assert.equal(countNewChatMessages(merged, 9, "2"), 0);
  const older = mergeChatMessages(merged, [message(1), message(2)]);
  assert.equal(countNewChatMessages(older, 5, "2"), 2);
});

test("약속 카드·전송 대기 영역이 화면을 채우면 가려진 수신 메시지는 읽음 대상에서 제외한다", () => {
  const viewport = { top: 100, bottom: 500, left: 0, right: 400 };
  assert.equal(isChatMessageEndVisible(viewport, { top: 0, bottom: 90, left: 10, right: 300 }), false);
  assert.equal(isChatMessageEndVisible(viewport, { top: 490, bottom: 550, left: 10, right: 300 }), false);
  assert.equal(isChatMessageEndVisible(viewport, { top: 400, bottom: 480, left: 10, right: 300 }), true);
  // 화면보다 긴 메시지는 첫 부분만 보일 때 제외, 끝부분을 확인한 경우 포함.
  assert.equal(isChatMessageEndVisible(viewport, { top: -500, bottom: 700, left: 10, right: 300 }), false);
  assert.equal(isChatMessageEndVisible(viewport, { top: -500, bottom: 450, left: 10, right: 300 }), true);
  assert.equal(isChatMessageEndVisible({ ...viewport, right: 0 }, { top: 200, bottom: 250, left: 0, right: 0 }), false);
});
