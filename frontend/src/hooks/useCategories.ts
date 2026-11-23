import { useEffect, useState } from "react";
import { type ApiCategory, getCategories } from "@/api/items";

/** 공통 카테고리 목록(B_013) — 화면 간 한 번만 불러와 재사용 */
let cache: ApiCategory[] | null = null;
let pending: Promise<ApiCategory[]> | null = null;

function load(): Promise<ApiCategory[]> {
  if (cache) return Promise.resolve(cache);
  pending ??= getCategories()
    .then((list) => {
      cache = list;
      return list;
    })
    .finally(() => {
      pending = null;
    });
  return pending;
}

export function useCategories(): ApiCategory[] | null {
  const [list, setList] = useState<ApiCategory[] | null>(cache);
  useEffect(() => {
    if (cache) return;
    let alive = true;
    load()
      .then((l) => alive && setList(l))
      .catch(() => alive && setList([]));
    return () => {
      alive = false;
    };
  }, []);
  return list;
}
