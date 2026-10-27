import { test } from "node:test";
import assert from "node:assert/strict";
import type { ApiChatMessage } from "../src/types/chat-api.ts";
import { countNewChatMessages, isChatAtBottom, mergeChatMessages } from "../src/utils/chat-state.ts";
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
