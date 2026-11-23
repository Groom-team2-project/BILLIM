import { api } from "@/api/client";
import type {
  ApiChatMessage,
  ApiChatMessagePage,
  ApiChatReadPosition,
  ApiChatRoom,
  ApiChatRoomPage,
  ApiChatUnreadCount,
  ChatRoomFilter,
  SendChatMessageBody,
} from "@/types/chat-api";

export type ChatRoomListParams = {
  filter?: ChatRoomFilter;
  page?: number;
  size?: number;
};

export function openChatRoom(itemId: string): Promise<ApiChatRoom> {
  return api<ApiChatRoom>("/chat/rooms", { method: "POST", json: { itemId } });
}

export function getChatRooms(params: ChatRoomListParams = {}): Promise<ApiChatRoomPage> {
  return api<ApiChatRoomPage>("/chat/rooms", { query: { ...params } });
}

export function getChatRoom(roomId: string): Promise<ApiChatRoom> {
  return api<ApiChatRoom>(`/chat/rooms/${encodeURIComponent(roomId)}`);
}

export type ChatMessageListParams = {
  afterSequence?: number;
  beforeSequence?: number;
  limit?: number;
};

export function getChatMessages(
  roomId: string,
  params: ChatMessageListParams = {},
): Promise<ApiChatMessagePage> {
  return api<ApiChatMessagePage>(`/chat/rooms/${encodeURIComponent(roomId)}/messages`, {
    query: { ...params },
  });
}

export function sendChatMessage(roomId: string, body: SendChatMessageBody): Promise<ApiChatMessage> {
  return api<ApiChatMessage>(`/chat/rooms/${encodeURIComponent(roomId)}/messages`, {
    method: "POST",
    json: body,
    idempotencyKey: body.clientMessageId,
  });
}

export function updateChatReadPosition(
  roomId: string,
  lastReadSequence: number,
): Promise<ApiChatReadPosition> {
  return api<ApiChatReadPosition>(`/chat/rooms/${encodeURIComponent(roomId)}/read`, {
    method: "PUT",
    json: { lastReadSequence },
  });
}

export function getChatUnreadCount(): Promise<ApiChatUnreadCount> {
  return api<ApiChatUnreadCount>("/chat/unread-count");
}

export function issueChatSocketTicket(roomId: string): Promise<{ ticket: string; expiresAt: string }> {
  return api(`/chat/rooms/${encodeURIComponent(roomId)}/socket-tickets`, { method: "POST" });
}
