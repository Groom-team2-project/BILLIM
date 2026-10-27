import type { ApiChatMessage } from "../types/chat-api.ts";

/** 소수점 스크롤 오차만 허용. 읽지 않은 메시지를 하단으로 잘못 판정하지 않음. */
export function isChatAtBottom(view: { scrollHeight: number; clientHeight: number; scrollTop: number }): boolean {
  return view.scrollHeight - view.clientHeight - view.scrollTop <= 2;
}

export function countNewChatMessages(messages: ApiChatMessage[], seenSequence: number, otherMemberId: string): number {
  return messages.filter((message) => message.sequence > seenSequence
    && message.type === "TEXT" && message.senderId === otherMemberId).length;
}

/** 초기 DESC·이전 DESC·새 메시지 ASC 응답을 화면 순서로 합친다. */
export function mergeChatMessages(previous: ApiChatMessage[], incoming: ApiChatMessage[]): ApiChatMessage[] {
  const byId = new Map(previous.map((message) => [message.id, message]));
  for (const message of incoming) byId.set(message.id, message);
  return [...byId.values()].sort((a, b) => a.sequence - b.sequence);
}
