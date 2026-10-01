export type * from "@/types/item";
import type { Category, Item } from "@/types/item";





/** 프로토타입 목데이터 — 백엔드 API 연동 시 교체 */

export const CATEGORIES: Category[] = [
  { id: "tool", label: "공구", icon: "tool" },
  { id: "camp", label: "캠핑", icon: "tent" },
  { id: "travel", label: "여행", icon: "luggage" },
  { id: "baby", label: "아기용품", icon: "baby" },
  { id: "music", label: "악기", icon: "music" },
  { id: "sports", label: "운동", icon: "sports" },
  { id: "kitchen", label: "주방", icon: "kitchen" },
  { id: "clean", label: "청소", icon: "broom" },
  { id: "life", label: "생활용품", icon: "cup" },
  { id: "etc", label: "기타", icon: "grid" },
];

export const CATEGORY_MAP: Record<string, Category> = Object.fromEntries(
  CATEGORIES.map((c) => [c.id, c]),
);

/** 지도 도식의 공용 장소 핀 — 좌표는 % 단위 (실제는 카카오맵) */
export const MAP_PINS = [
  { id: "a", x: 30, y: 38, label: "관리동 무인택배함", n: 4 },
  { id: "b", x: 64, y: 30, label: "B동 커뮤니티센터", n: 3 },
  { id: "c", x: 50, y: 70, label: "정문 경비실", n: 2 },
  { id: "d", x: 80, y: 62, label: "놀이터 정자", n: 2 },
  { id: "e", x: 16, y: 76, label: "C동 자전거 보관소", n: 1 },
] as const;

export const MOCK_ITEMS: Item[] = [
  { id: 1, title: "충전식 전동드릴 12V", cat: "tool", owner: "정우", place: "관리동 1층 무인택배함 앞", dist: "180m", avail: "ok", trust: { kept: 12, total: 12, done: 18, lent: 15, since: "2026년 3월" }, ago: "10분 전", photos: 4 },
  { id: 2, title: "캠핑 의자 2개 세트", cat: "camp", owner: "서연", place: "B동 커뮤니티센터 입구", dist: "320m", avail: "partial", availText: "9/27 예약됨", trust: { kept: 5, total: 5, done: 7, since: "2026년 5월" }, ago: "1시간 전", photos: 3 },
  { id: 3, title: "기내용 캐리어 20인치", cat: "travel", owner: "민재", place: "정문 경비실 옆 벤치", dist: "90m", avail: "ok", trust: { kept: 3, total: 3, done: 4, since: "2026년 7월" }, ago: "3시간 전", photos: 2 },
  { id: 4, title: "스팀 청소기", cat: "clean", owner: "하은", place: "놀이터 옆 정자", dist: "250m", avail: "ok", trust: { kept: 9, total: 10, late: "1회 하루 늦음", done: 11, since: "2026년 2월" }, ago: "어제", photos: 3 },
  { id: 5, title: "원터치 텐트 4인용", cat: "camp", owner: "도윤", place: "관리동 1층 무인택배함 앞", dist: "180m", avail: "ok", trust: { kept: 0, total: 0, done: 0, since: "2026년 9월" }, ago: "어제", photos: 5 },
  { id: 6, title: "3단 접이식 사다리", cat: "tool", owner: "지호", place: "C동 자전거 보관소", dist: "410m", avail: "none", availText: "9/30까지 대여 중", trust: { kept: 6, total: 6, done: 6, since: "2026년 4월" }, ago: "2일 전", photos: 2 },
  { id: 7, title: "제습기 10L", cat: "life", owner: "나", place: "B동 커뮤니티센터 입구", dist: "320m", avail: "ok", trust: { kept: 4, total: 4, done: 9 }, ago: "3일 전", photos: 2 },
  { id: 8, title: "아이스박스 25L", cat: "camp", owner: "나", place: "정문 경비실 옆 벤치", dist: "90m", avail: "ok", trust: { kept: 4, total: 4, done: 9 }, ago: "4일 전", photos: 3 },
];
