export type * from "@/types/chat";
import type { Chat, Appointment } from "@/types/chat";


/** 프로토타입 목데이터 — 백엔드 API 연동 시 교체 */
export const CHATS: Chat[] = [
  { id: "c1", name: "정우", item: "충전식 전동드릴 12V", cat: "tool", status: "approved", role: "borrowed", last: "넵, 비트 케이스도 챙겨둘게요.", time: "오후 3:12", unread: 1, appt: "9/26(토) 오전 10:00 약속" },
  { id: "c2", name: "지민", item: "아이스박스 25L", cat: "camp", status: "requested", role: "lent", last: "혹시 금요일 저녁에 받아도 될까요?", time: "오후 1:40", unread: 2 },
  { id: "c3", name: "서연", item: "캠핑 의자 2개 세트", cat: "camp", status: "active", role: "lent", last: "내일 저녁 7시에 반납할게요!", time: "어제", unread: 0, appt: "9/23(수) 오후 7:00 반납" },
  { id: "c4", name: "민재", item: "기내용 캐리어 20인치", cat: "travel", status: "approved", role: "borrowed", last: "약속이 확정됐어요.", time: "9/20", unread: 0, appt: "9/24(목) 오후 7:30 약속" },
  { id: "c5", name: "하은", item: "스팀 청소기", cat: "clean", status: "rejected", role: "lent", last: "다음에 꼭 빌려드릴게요.", time: "9/18", unread: 0 },
];


export const MOCK_APPT: Appointment = {
  kind: "전달",
  date: "9/26(토)",
  time: "오전 10:00",
  place: "관리동 1층 무인택배함 앞",
};
