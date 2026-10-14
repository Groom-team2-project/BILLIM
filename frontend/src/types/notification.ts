/** 알림 도메인 타입 */
export type NotificationTone = "requested" | "approved" | "returned" | "rejected" | "canceled";

export type Notification = {
  tone: NotificationTone;
  icon: string;
  title: string;
  subtitle: string;
  unread: boolean;
  to: string;
};

/** D_048~D_051 알림 API 응답 */
export type ApiNotification = {
  id: string;
  type: string;
  title: string;
  targetType: "RENTAL" | "REPORT" | "MEMBER_SELF";
  targetId: string;
  readAt?: string;
  createdAt: string;
};

export type ApiNotificationPage = {
  items: ApiNotification[];
  hasMore: boolean;
  nextCursor?: string;
};

export type ApiNotificationUnreadCount = {
  unreadCount: number;
};
