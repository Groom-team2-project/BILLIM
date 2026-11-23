export type PendingChatMessage = {
  clientMessageId: string;
  body: string;
  rentalId?: string;
  failed: boolean;
};

const PREFIX = "billim-chat-pending:";

function key(memberId: string, roomId: string): string {
  return `${PREFIX}${memberId}:${roomId}`;
}

export function readPendingChatMessages(memberId: string | null, roomId: string): PendingChatMessage[] {
  if (!memberId) return [];
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(key(memberId, roomId)) ?? "[]");
    if (!Array.isArray(value)) return [];
    return value.filter((entry): entry is PendingChatMessage =>
      typeof entry === "object" && entry !== null &&
      typeof entry.clientMessageId === "string" && typeof entry.body === "string" &&
      (entry.rentalId === undefined || typeof entry.rentalId === "string"))
      .map((entry) => ({ ...entry, failed: true }));
  } catch {
    return [];
  }
}

export function writePendingChatMessages(memberId: string | null, roomId: string, messages: PendingChatMessage[]): void {
  if (!memberId) return;
  try {
    if (messages.length === 0) sessionStorage.removeItem(key(memberId, roomId));
    else sessionStorage.setItem(key(memberId, roomId), JSON.stringify(messages));
  } catch {
    // 브라우저 저장 공간이 없어도 현재 화면의 재전송은 유지한다.
  }
}

export function clearPendingChatMessages(): void {
  try {
    for (let index = sessionStorage.length - 1; index >= 0; index--) {
      const name = sessionStorage.key(index);
      if (name?.startsWith(PREFIX)) sessionStorage.removeItem(name);
    }
  } catch {
    // 저장 공간에 접근할 수 없으면 정리할 값도 읽을 수 없다.
  }
}
