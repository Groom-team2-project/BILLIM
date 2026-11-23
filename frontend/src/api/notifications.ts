import { api } from "@/api/client";
import type {
  ApiNotification,
  ApiNotificationPage,
  ApiNotificationUnreadCount,
} from "@/types/notification";

export type NotificationListParams = {
  cursor?: string;
  limit?: number;
  read?: boolean;
};

export function getNotifications(params: NotificationListParams = {}): Promise<ApiNotificationPage> {
  return api<ApiNotificationPage>("/notifications", {
    query: {
      cursor: params.cursor,
      limit: params.limit,
      read: params.read === undefined ? undefined : String(params.read),
    },
  });
}

export function getUnreadNotificationCount(): Promise<ApiNotificationUnreadCount> {
  return api<ApiNotificationUnreadCount>("/notifications/unread-count");
}

export function markNotificationRead(notificationId: string): Promise<ApiNotification> {
  return api<ApiNotification>(`/notifications/${encodeURIComponent(notificationId)}/read`, {
    method: "PUT",
  });
}

export function markAllNotificationsRead(throughId: string): Promise<void> {
  return api<void>("/notifications/read-all", {
    method: "PUT",
    json: { throughId },
  });
}
