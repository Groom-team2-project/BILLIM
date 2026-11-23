export type * from "@/types/notification";
import type { Notification } from "@/types/notification";


/** 프로토타입 목데이터 — 사건 유형별 알림. API 연동 시 교체 */
export const NOTIFICATIONS: Notification[] = [
  { tone: "approved", icon: "check", title: "정우 님이 대여 요청을 승인했어요", subtitle: "충전식 전동드릴 12V · 방금", unread: true, to: "/chat/c1" },
  { tone: "requested", icon: "calendar", title: "정우 님이 전달 약속을 제안했어요", subtitle: "9/26(토) 오전 10:00 · 관리동 무인택배함 · 방금", unread: true, to: "/chat/c1" },
  { tone: "requested", icon: "ring", title: "지민 님이 대여를 요청했어요", subtitle: "아이스박스 25L · 9/26 – 9/28 · 10분 전", unread: true, to: "/rentals" },
  { tone: "returned", icon: "returned", title: "서연 님이 반납을 확인했어요", subtitle: "캠핑 의자 2개 세트 · 어제", unread: false, to: "/rentals" },
  { tone: "rejected", icon: "x", title: "도윤 님이 요청을 거절했어요", subtitle: "원터치 텐트 4인용 · 다른 날짜로 다시 요청할 수 있어요 · 2일 전", unread: false, to: "/rentals" },
  { tone: "canceled", icon: "dash", title: "태오 님이 요청을 취소했어요", subtitle: "아이스박스 25L · 2일 전", unread: false, to: "/rentals" },
];
