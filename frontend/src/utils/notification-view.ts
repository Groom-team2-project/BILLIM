import type { ApiNotification, NotificationTone } from "@/types/notification";

type NotificationAppearance = { tone: NotificationTone; icon: string };

const APPEARANCE: Record<string, NotificationAppearance> = {
  RENTAL_REQUESTED: { tone: "requested", icon: "ring" },
  RENTAL_APPROVED: { tone: "approved", icon: "check" },
  RENTAL_REJECTED: { tone: "rejected", icon: "x" },
  RENTAL_CANCELED: { tone: "canceled", icon: "dash" },
  RENTAL_HANDED_OVER: { tone: "approved", icon: "check" },
  RENTAL_RETURNED: { tone: "returned", icon: "returned" },
  RENTAL_EXPIRED: { tone: "canceled", icon: "clock" },
  APPOINTMENT_PROPOSED: { tone: "requested", icon: "calendar" },
  APPOINTMENT_CONFIRMED: { tone: "approved", icon: "calendar" },
  APPOINTMENT_WITHDRAWN: { tone: "canceled", icon: "calendar" },
  REPORT_RESOLVED: { tone: "returned", icon: "flag" },
};

export function notificationAppearance(type: string): NotificationAppearance {
  return APPEARANCE[type] ?? { tone: "requested", icon: "bell" };
}

export function notificationTime(createdAt: string): string {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(createdAt));
}

/** 채팅에서 바로 처리할 수 있는 거래 알림. 연결된 방이 없으면 대여 화면으로 이동한다. */
export function notificationOpensChat(notification: ApiNotification): boolean {
  return notification.targetType === "RENTAL" && (
    notification.type === "RENTAL_REQUESTED" ||
    notification.type === "RENTAL_APPROVED" ||
    notification.type.startsWith("APPOINTMENT_")
  );
}

export async function notificationDestination(
  notification: ApiNotification,
  getRentalRoom: (rentalId: string) => Promise<{ chatRoomId?: string }>,
): Promise<string> {
  const fallback = notificationTargetPath(notification);
  if (!notificationOpensChat(notification)) return fallback;
  const rental = await getRentalRoom(notification.targetId);
  return rental.chatRoomId
    ? `/chat/${encodeURIComponent(rental.chatRoomId)}?rentalId=${encodeURIComponent(notification.targetId)}`
    : fallback;
}

export function notificationTargetPath(notification: ApiNotification): string {
  switch (notification.targetType) {
    case "RENTAL":
      return `/rentals?rentalId=${encodeURIComponent(notification.targetId)}`;
    case "REPORT":
      return "/reports";
    case "MEMBER_SELF":
      return "/profile";
  }
}
