export type * from "@/types/admin";
import type { CommStats, Report, Notice } from "@/types/admin";
/** 관리자 콘솔 목데이터 — API 연동 시 교체 */

export const COMM: Record<string, CommStats> = {
  "새솔마을 5단지": {
    items: 46, active: 24, newItems: 12, newUsers: 8, rate: 82,
    week: [["월", 8], ["화", 12], ["수", 9], ["목", 14], ["금", 11], ["토", 18], ["일", 15]],
    cats: [["공구", 14], ["캠핑", 9], ["생활용품", 8], ["여행", 7], ["운동", 5], ["주방", 4], ["아기용품", 4], ["악기", 2], ["청소", 2], ["기타", 3]],
    popular: [["충전식 전동드릴 12V", 9], ["캠핑 의자 2개 세트", 7], ["아이스박스 25L", 6]],
  },
  "새솔마을 6단지": {
    items: 28, active: 11, newItems: 5, newUsers: 3, rate: 76,
    week: [["월", 3], ["화", 5], ["수", 4], ["목", 6], ["금", 5], ["토", 9], ["일", 7]],
    cats: [["공구", 6], ["생활용품", 5], ["캠핑", 5], ["주방", 3], ["여행", 3], ["운동", 2], ["아기용품", 1], ["악기", 1], ["청소", 1], ["기타", 1]],
    popular: [["원터치 텐트 4인용", 5], ["무선 청소기", 4], ["보드게임 세트", 3]],
  },
  "한빛초 학부모방": {
    items: 19, active: 7, newItems: 4, newUsers: 6, rate: 88,
    week: [["월", 2], ["화", 3], ["수", 3], ["목", 4], ["금", 3], ["토", 5], ["일", 4]],
    cats: [["아기용품", 6], ["생활용품", 4], ["공구", 2], ["주방", 2], ["악기", 2], ["운동", 1], ["캠핑", 1], ["여행", 1], ["청소", 0], ["기타", 0]],
    popular: [["유아 카시트", 6], ["보행기", 4], ["유아 자전거", 3]],
  },
};

export const COMM_ORDER = ["새솔마을 5단지", "새솔마을 6단지", "한빛초 학부모방"];




export const INITIAL_REPORTS: Report[] = [
  { id: 1, name: "정우", reason: "돈이나 보증금을 요구했어요", date: "9/15", community: "새솔마을 5단지", status: "pending", action: null },
  { id: 2, name: "민재", reason: "사기가 의심돼요", date: "9/21", community: "새솔마을 5단지", status: "pending", action: null },
  { id: 3, name: "도윤", reason: "약속을 반복해서 어겨요", date: "8/20", community: "새솔마을 6단지", status: "done", action: "경고" },
  { id: 4, name: "하은", reason: "물건 상태가 설명과 크게 달라요", date: "8/12", community: "새솔마을 5단지", status: "done", action: "반려(문제없음)" },
];


export const INITIAL_NOTICES: Notice[] = [
  { id: 101, title: "추석 연휴 거래 안전 안내", date: "9/20", pinned: true },
  { id: 102, title: "커뮤니티 규칙 업데이트 안내", date: "9/5", pinned: false },
];
