import { useSyncExternalStore } from "react";
import type { ApiItemDetail } from "@/api/items";

/**
 * 지금 보고 있는 물건 상세(B_020) — 상세 화면과 앱 헤더 더보기(⋮) 메뉴가 같은 응답을 공유한다.
 * 상세 화면이 불러오거나 바꾼 값을 넣고, 화면을 떠나면 비운다.
 */
let current: ApiItemDetail | null = null;
const subs = new Set<() => void>();

export function setCurrentItem(item: ApiItemDetail | null) {
  current = item;
  for (const f of subs) f();
}

function subscribe(f: () => void) {
  subs.add(f);
  return () => {
    subs.delete(f);
  };
}

/** itemId가 주어지면 그 물건일 때만 돌려준다 */
export function useCurrentItem(itemId?: string): ApiItemDetail | null {
  const item = useSyncExternalStore(subscribe, () => current);
  return item && (itemId === undefined || item.id === itemId) ? item : null;
}
