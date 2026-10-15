import type { ApiChatMessage } from "@/types/chat-api";

export function chatSocketUrl(roomId: string, pageUrl: string): string {
  const url = new URL("/ws/chat", pageUrl);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.searchParams.set("roomId", roomId);
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
