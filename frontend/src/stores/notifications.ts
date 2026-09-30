import { useSyncExternalStore } from "react";
import { NOTIFICATIONS } from "@/data/notifications";
import type { Notification } from "@/types/notification";

/** 알림 읽음 상태 — 헤더 뱃지와 목록이 같은 값을 본다 */
let items: Notification[] = NOTIFICATIONS;
const subs = new Set<() => void>();

function subscribe(f: () => void) {
  subs.add(f);
  return () => {
    subs.delete(f);
  };
}

export function useNotifications(): Notification[] {
  return useSyncExternalStore(subscribe, () => items);
}

export function useUnreadCount(): number {
  return useSyncExternalStore(subscribe, () => items.filter((n) => n.unread).length);
}

export function markAllRead() {
  if (!items.some((n) => n.unread)) return;
  items = items.map((n) => (n.unread ? { ...n, unread: false } : n));
  for (const f of subs) f();
}
