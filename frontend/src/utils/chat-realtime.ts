import type { ApiChatMessage } from "@/types/chat-api";

export function chatSocketUrl(roomId: string, pageUrl: string, endpoint?: string, ticket?: string): string {
  const url = new URL(endpoint || "/ws/chat", pageUrl);
  if (url.username || url.password || url.hash || url.search
    || !["http:", "https:", "ws:", "wss:"].includes(url.protocol)) {
    throw new Error("채팅 WebSocket 주소 설정 오류");
  }
  if (url.protocol === "https:") url.protocol = "wss:";
  if (url.protocol === "http:") url.protocol = "ws:";
  if (new URL(pageUrl).protocol === "https:" && url.protocol !== "wss:") {
    throw new Error("운영 채팅 연결은 WSS 필요");
  }
  url.searchParams.set("roomId", roomId);
  if (ticket) url.searchParams.set("ticket", ticket);
  return url.toString();
}

export function parseChatSocketMessage(data: unknown, roomId: string): ApiChatMessage | null {
  if (typeof data !== "string") return null;

  try {
    const value: unknown = JSON.parse(data);
    if (typeof value !== "object" || value === null) return null;

    const message = value as Partial<ApiChatMessage>;
    if (message.roomId !== roomId
      || typeof message.id !== "string"
      || typeof message.sequence !== "number"
      || !Number.isSafeInteger(message.sequence)
      || message.sequence < 1
      || (message.type !== "TEXT" && message.type !== "SYSTEM")
      || typeof message.createdAt !== "string") {
      return null;
    }

    return message as ApiChatMessage;
  } catch {
    return null;
  }
}
