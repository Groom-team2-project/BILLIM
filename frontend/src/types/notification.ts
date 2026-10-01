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
