import { test } from "node:test";
import assert from "node:assert/strict";
import { chatSocketUrl, parseChatSocketMessage } from "../src/utils/chat-realtime.ts";

test("채팅 연결은 현재 출처를 사용하고 방 ID를 쿼리로 전달한다", () => {
  assert.equal(chatSocketUrl("12", "http://localhost:5173/chat"), "ws://localhost:5173/ws/chat?roomId=12");
  assert.equal(chatSocketUrl("12", "https://billim.example/chat"), "wss://billim.example/ws/chat?roomId=12");
});

test("별도 WSS 주소에 방과 일회용 연결권을 인코딩한다", () => {
  assert.equal(chatSocketUrl("12", "https://billim.netlify.app/chat", "wss://socket.example/ws/chat", "a_b-C"),
    "wss://socket.example/ws/chat?roomId=12&ticket=a_b-C");
  assert.equal(chatSocketUrl("12", "https://billim.netlify.app/chat", "https://socket.example/ws/chat"),
    "wss://socket.example/ws/chat?roomId=12");
});

test("운영 평문 연결과 비밀값·쿼리가 포함된 설정 주소를 거부한다", () => {
  for (const endpoint of ["ws://socket.example/ws/chat", "http://socket.example/ws/chat",
    "wss://user:password@socket.example/ws/chat", "wss://socket.example/ws/chat?ticket=stored",
    "wss://socket.example/ws/chat#fragment", "ftp://socket.example/ws/chat"]) {
    assert.throws(() => chatSocketUrl("12", "https://billim.netlify.app/chat", endpoint));
  }
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
