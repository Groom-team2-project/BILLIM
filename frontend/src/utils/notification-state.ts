import type { ApiNotification } from "@/types/notification";

export function appendNotificationPage(
  current: ApiNotification[],
  incoming: ApiNotification[],
): ApiNotification[] {
  const loadedIds = new Set(current.map((item) => item.id));
  return [...current, ...incoming.filter((item) => !loadedIds.has(item.id))];
}

export function applyNotificationRead(
  current: ApiNotification[],
  unreadCount: number,
  updated: ApiNotification,
): { items: ApiNotification[]; unreadCount: number } {
  const wasUnread = current.some((item) => item.id === updated.id && !item.readAt);
  return {
    items: current.map((item) => item.id === updated.id ? updated : item),
    unreadCount: Math.max(0, unreadCount - (wasUnread ? 1 : 0)),
  };
}

/** id 내림차순 목록에서 서버의 모두 읽음 기준으로 관측한 최신 ID */
export function observedThroughId(items: ApiNotification[]): string | undefined {
  return items[0]?.id;
}
