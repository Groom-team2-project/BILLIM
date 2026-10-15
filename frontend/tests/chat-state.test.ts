import { test } from "node:test";
import assert from "node:assert/strict";
import type { ApiChatMessage } from "../src/types/chat-api.ts";
import { mergeChatMessages } from "../src/utils/chat-state.ts";
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
