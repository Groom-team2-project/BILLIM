import type { ApiChatMessage, ApiChatRoom } from "../types/chat-api.ts";

/** 초기 DESC·이전 DESC·새 메시지 ASC 응답을 화면 순서로 합친다. */
export function mergeChatMessages(previous: ApiChatMessage[], incoming: ApiChatMessage[]): ApiChatMessage[] {
  const byId = new Map(previous.map((message) => [message.id, message]));
  for (const message of incoming) byId.set(message.id, message);
  return [...byId.values()].sort((a, b) => a.sequence - b.sequence);
}

/** 열린 방의 새 메시지만 목록 미리보기에 반영한다. 오래된 조회 응답은 무시한다. */
export function updateRoomPreview(room: ApiChatRoom, message: ApiChatMessage): ApiChatRoom {
  if (room.id !== message.roomId) return room;
  if (room.lastMessageAt && Date.parse(room.lastMessageAt) > Date.parse(message.createdAt)) return room;
  const preview = message.type === "SYSTEM"
    ? message.system?.label ?? message.body ?? "거래 상태가 변경됐어요"
    : message.body ?? "";
  const limitedPreview = preview.slice(0, 200);
  if (room.lastMessageAt === message.createdAt && room.lastMessagePreview === limitedPreview) return room;
  return { ...room, lastMessagePreview: limitedPreview, lastMessageAt: message.createdAt };
}
