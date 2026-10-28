import { test } from "node:test";
import assert from "node:assert/strict";
import {
  clearPendingChatMessages,
  readPendingChatMessages,
  writePendingChatMessages,
} from "../src/utils/chat-pending.ts";

test("새로고침 뒤 전송 결과가 불확실한 메시지를 같은 ID로 재시도할 수 있다", () => {
  const values = new Map<string, string>();
  const storage = {
    get length() { return values.size; },
    key(index: number) { return [...values.keys()][index] ?? null; },
    getItem(name: string) { return values.get(name) ?? null; },
    setItem(name: string, value: string) { values.set(name, value); },
    removeItem(name: string) { values.delete(name); },
  };
  const original = Object.getOwnPropertyDescriptor(globalThis, "sessionStorage");
  Object.defineProperty(globalThis, "sessionStorage", { configurable: true, value: storage });
  try {
    const message = { clientMessageId: "same-request-id", body: "안녕하세요", failed: false };
    writePendingChatMessages("member-1", "room-1", [message]);
    assert.deepEqual(readPendingChatMessages("member-1", "room-1"), [{ ...message, failed: true }]);
    assert.deepEqual(readPendingChatMessages("member-2", "room-1"), []);
    clearPendingChatMessages();
    assert.deepEqual(readPendingChatMessages("member-1", "room-1"), []);
  } finally {
    if (original) Object.defineProperty(globalThis, "sessionStorage", original);
    else Reflect.deleteProperty(globalThis, "sessionStorage");
  }
});
