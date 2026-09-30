export type * from "@/types/rental";
import type { Rental } from "@/types/rental";
/** 대여 상태(서버 7종 기준 화면 뷰) — 명세 확정 기준
 *  OVERDUE는 파생 표시 (정책 및 상태.md)
 */



/** 프로토타입 목데이터 — 백엔드 API 연동 시 교체 */

export const BORROWED: Rental[] = [
  { id: "b1", title: "캠핑 의자 2개 세트", cat: "camp", owner: "서연", status: "active", start: [9, 20], end: [9, 23], dday: "반납 D-1", place: "B동 커뮤니티센터 입구" },
  { id: "b2", title: "기내용 캐리어 20인치", cat: "travel", owner: "민재", status: "approved", start: [9, 24], end: [9, 28], dday: "받기 D-2", place: "정문 경비실 옆 벤치", appt: "9/24(목) 오후 7:30 전달" },
  { id: "b3", title: "충전식 전동드릴 12V", cat: "tool", owner: "정우", status: "requested", start: [9, 26], end: [9, 27], place: "관리동 1층 무인택배함 앞" },
  { id: "b4", title: "스팀 청소기", cat: "clean", owner: "하은", status: "rejected", start: [9, 25], end: [9, 26], place: "놀이터 옆 정자", reason: "그날 제가 쓸 일이 생겼어요" },
  { id: "b5", title: "원터치 텐트 4인용", cat: "camp", owner: "도윤", status: "returned", start: [9, 12], end: [9, 14], place: "관리동 1층 무인택배함 앞" },
];

export const LENT: Rental[] = [
  { id: "l1", title: "아이스박스 25L", cat: "camp", borrower: "지민", trustLine: "반납 약속 7/7", status: "requested", start: [9, 26], end: [9, 28], place: "정문 경비실 옆 벤치" },
  { id: "l2", title: "아이스박스 25L", cat: "camp", borrower: "태오", trustLine: "반납 약속 2/2", status: "requested", start: [9, 27], end: [9, 28], place: "정문 경비실 옆 벤치" },
  { id: "l3", title: "제습기 10L", cat: "life", borrower: "태오", status: "approved", start: [9, 23], end: [9, 25], dday: "전달 D-1", place: "B동 커뮤니티센터 입구", appt: "9/23(수) 오전 8:00 전달" },
  { id: "l4", title: "3단 접이식 사다리", cat: "tool", borrower: "하은", status: "overdue", start: [9, 19], end: [9, 21], dday: "반납 1일 지남", place: "C동 자전거 보관소" },
];
