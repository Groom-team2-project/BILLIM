import { useSyncExternalStore } from "react";
import type { CategoryId } from "@/types/item";

/** 물건 공개 상태 — 정책 및 상태.md
 *  PUBLIC 공개 중 / HIDDEN 공개 중지(검색·신규 요청 불가, 진행 중 대여 유지) / DELETED 논리 삭제
 *  API 연동 시 B_022(visibility) · B_023(논리 삭제)로 교체
 */
export type Visibility = "PUBLIC" | "HIDDEN";

export type MyItem = {
  id: number;
  title: string;
  cat: CategoryId;
  place: string;
  /** 진행 중 요청 수 — 1건 이상이면 삭제 불가 */
  requests: number;
  visibility: Visibility;
};

/** 내가 등록한 물건 목데이터 — API 연동 시 B_021 목록으로 교체 */
let myItems: MyItem[] = [
  { id: 8, title: "아이스박스 25L", cat: "camp", place: "정문 경비실 옆 벤치", requests: 2, visibility: "PUBLIC" },
  { id: 4, title: "스팀 청소기", cat: "clean", place: "관리동 무인택배함 앞", requests: 0, visibility: "HIDDEN" },
];

const subs = new Set<() => void>();

function emit() {
  for (const f of subs) f();
}

function subscribe(f: () => void) {
  subs.add(f);
  return () => {
    subs.delete(f);
  };
}

export function useMyItems(): MyItem[] {
  return useSyncExternalStore(subscribe, () => myItems);
}

export function useVisibility(id: number): Visibility {
  return useSyncExternalStore(subscribe, () => myItems.find((i) => i.id === id)?.visibility ?? "PUBLIC");
}

export function setVisibility(id: number, next: Visibility) {
  myItems = myItems.map((i) => (i.id === id ? { ...i, visibility: next } : i));
  emit();
}

/** 논리 삭제 — 진행 중 요청이 있으면 거부 */
export function deleteItem(id: number): boolean {
  const target = myItems.find((i) => i.id === id);
  if (!target || target.requests > 0) return false;
  myItems = myItems.filter((i) => i.id !== id);
  emit();
  return true;
}
