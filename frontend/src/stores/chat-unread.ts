import { useSyncExternalStore } from "react";
import { getChatUnreadCount } from "@/api/chat";

let unreadCount = 0;
let version = 0;
const subscribers = new Set<() => void>();

function subscribe(listener: () => void) {
  subscribers.add(listener);
  return () => subscribers.delete(listener);
}

function update(count: number) {
  unreadCount = count;
  subscribers.forEach((listener) => listener());
}

export function useChatUnreadCount(): number {
  return useSyncExternalStore(subscribe, () => unreadCount);
}

export function clearChatUnreadCount(): void {
  ++version;
  update(0);
}

export async function refreshChatUnreadCount(): Promise<void> {
  const requestVersion = ++version;
  try {
    const result = await getChatUnreadCount();
    if (requestVersion === version) update(result.unreadCount);
  } catch {
    // 일시적인 조회 실패에는 마지막으로 확인한 배지를 유지한다.
  }
}
