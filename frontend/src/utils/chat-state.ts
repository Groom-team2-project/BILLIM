import type { ApiChatMessage } from "../types/chat-api.ts";

/** 메시지 끝부분의 가시성 확인. 긴 메시지도 마지막 부분을 확인한 뒤 읽음 갱신. */
export function isChatMessageEndVisible(
  viewport: { top: number; bottom: number; left: number; right: number },
  message: { top: number; bottom: number; left: number; right: number },
): boolean {
  return viewport.bottom > viewport.top && viewport.right > viewport.left
    && message.bottom > message.top && message.right > message.left
    && message.bottom > viewport.top && message.bottom <= viewport.bottom + 1
    && message.right > viewport.left && message.left < viewport.right;
}

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
