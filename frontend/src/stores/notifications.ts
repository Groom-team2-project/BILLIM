import { useSyncExternalStore } from "react";
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/api/notifications";
import type { ApiNotification } from "@/types/notification";
import { appendNotificationPage, applyNotificationRead, observedThroughId } from "@/utils/notification-state";

type NotificationState = {
  items: ApiNotification[];
  unreadCount: number;
  hasMore: boolean;
  nextCursor?: string;
  loading: boolean;
  loadingMore: boolean;
  readingAll: boolean;
  error: string | null;
  errorKind: "refresh" | "more" | null;
};

let state: NotificationState = {
  items: [],
  unreadCount: 0,
  hasMore: false,
  loading: false,
  loadingMore: false,
  readingAll: false,
  error: null,
  errorKind: null,
};

let listVersion = 0;
let sessionVersion = 0;
let countVersion = 0;
const subscribers = new Set<() => void>();

function subscribe(listener: () => void) {
  subscribers.add(listener);
  return () => subscribers.delete(listener);
}

function update(next: Partial<NotificationState>) {
  state = { ...state, ...next };
  subscribers.forEach((listener) => listener());
}

export function useNotificationState(): NotificationState {
  return useSyncExternalStore(subscribe, () => state);
}

export function useUnreadCount(): number {
  return useSyncExternalStore(subscribe, () => state.unreadCount);
}

export function clearNotifications(): void {
  ++listVersion;
  ++sessionVersion;
  ++countVersion;
  update({
    items: [],
    unreadCount: 0,
    hasMore: false,
    nextCursor: undefined,
    loading: false,
    loadingMore: false,
    readingAll: false,
    error: null,
    errorKind: null,
  });
}

export async function refreshUnreadCount(): Promise<boolean> {
  const version = sessionVersion;
  const currentCountVersion = ++countVersion;
  try {
    const result = await getUnreadNotificationCount();
    if (version !== sessionVersion || currentCountVersion !== countVersion) return false;
    update({ unreadCount: result.unreadCount });
    return true;
  } catch {
    return false;
  }
}

export async function refreshNotifications(silent = false): Promise<void> {
  if (silent && (state.loading || state.loadingMore || state.readingAll)) return;
  const version = ++listVersion;
  const currentCountVersion = ++countVersion;
  if (!silent) update({ loading: true, loadingMore: false, error: null, errorKind: null });

  void getUnreadNotificationCount().then((count) => {
    if (version === listVersion && currentCountVersion === countVersion) {
      update({ unreadCount: count.unreadCount });
    }
  }).catch(() => {
    // 목록 요청과 별개로 기존 안 읽은 수를 유지한다.
  });

  try {
    const page = await getNotifications();
    if (version !== listVersion) return;

    update({
      items: silent ? appendNotificationPage(page.items, state.items) : page.items,
      hasMore: silent && state.items.length > page.items.length ? state.hasMore : page.hasMore,
      nextCursor: silent && state.items.length > page.items.length ? state.nextCursor : page.nextCursor,
      loading: false,
      loadingMore: false,
      error: null,
      errorKind: null,
    });
  } catch {
    if (version !== listVersion) return;
    if (silent) update({ loadingMore: false });
    else update({ loading: false, error: "알림을 불러오지 못했어요.", errorKind: "refresh" });
  }
}

export async function loadMoreNotifications(): Promise<void> {
  if (state.loading || state.loadingMore || !state.hasMore || !state.nextCursor) return;

  const version = listVersion;
  const cursor = state.nextCursor;
  update({ loadingMore: true, error: null, errorKind: null });

  try {
    const page = await getNotifications({ cursor });
    if (version !== listVersion) return;

    update({
      items: appendNotificationPage(state.items, page.items),
      hasMore: page.hasMore,
      nextCursor: page.nextCursor,
      loadingMore: false,
      errorKind: null,
    });
  } catch {
    if (version !== listVersion) return;
    update({ loadingMore: false, error: "다음 알림을 불러오지 못했어요.", errorKind: "more" });
  }
}

export async function markOneRead(id: string): Promise<void> {
  const version = sessionVersion;
  const updated = await markNotificationRead(id);
  if (version !== sessionVersion) return;
  ++listVersion;
  ++countVersion;
  update(applyNotificationRead(state.items, state.unreadCount, updated));
}

export async function markAllRead(): Promise<void> {
  const throughId = observedThroughId(state.items);
  if (!throughId || state.readingAll || state.unreadCount === 0) return;

  const version = sessionVersion;
  update({ readingAll: true });
  try {
    await markAllNotificationsRead(throughId);
    if (version !== sessionVersion) return;
    await refreshNotifications();
  } finally {
    if (version === sessionVersion) update({ readingAll: false });
  }
}
