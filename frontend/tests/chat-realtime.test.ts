import { test } from "node:test";
import assert from "node:assert/strict";
import { chatSocketUrl, parseChatSocketMessage } from "../src/utils/chat-realtime.ts";

test("채팅 연결은 현재 출처를 사용하고 방 ID를 쿼리로 전달한다", () => {
  assert.equal(chatSocketUrl("12", "http://localhost:5173/chat"), "ws://localhost:5173/ws/chat?roomId=12");
  assert.equal(chatSocketUrl("12", "https://billim.example/chat"), "wss://billim.example/ws/chat?roomId=12");
});

test("실시간 메시지는 연결한 방의 유효한 메시지만 받아들인다", () => {
  const message = JSON.stringify({
    id: "3", roomId: "12", sequence: 3, type: "TEXT", body: "안녕하세요",
    createdAt: "2026-10-14T00:00:00Z",
  });
  assert.equal(parseChatSocketMessage(message, "12")?.sequence, 3);
  assert.equal(parseChatSocketMessage(message, "13"), null);
  assert.equal(parseChatSocketMessage("{", "12"), null);
  assert.equal(parseChatSocketMessage(JSON.stringify({ roomId: "12", sequence: -1 }), "12"), null);
});
