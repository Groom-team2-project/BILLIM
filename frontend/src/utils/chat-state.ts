import type { ApiChatMessage } from "../types/chat-api.ts";

/** 초기 DESC·이전 DESC·새 메시지 ASC 응답을 화면 순서로 합친다. */
export function mergeChatMessages(previous: ApiChatMessage[], incoming: ApiChatMessage[]): ApiChatMessage[] {
  const byId = new Map(previous.map((message) => [message.id, message]));
  for (const message of incoming) byId.set(message.id, message);
  return [...byId.values()].sort((a, b) => a.sequence - b.sequence);
}
