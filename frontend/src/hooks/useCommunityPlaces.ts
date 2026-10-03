import { useEffect, useState } from "react";
import { type ApiPlace, getMyCommunityPlaces } from "@/api/items";

/**
 * 내 동네의 공용 장소(E_012) — 장소 ID → 장소.
 * 물건 응답에는 동네(E) 연동 전까지 장소 이름·좌표가 없어서, 화면에서 이 목록으로 채운다.
 * 화면 간 한 번만 불러와 재사용하고, 실패하면 빈 목록(이름 없이 표시)이며 다음에 다시 시도한다.
 */
let cache: ReadonlyMap<string, ApiPlace> | null = null;
let pending: Promise<ReadonlyMap<string, ApiPlace>> | null = null;

function load(): Promise<ReadonlyMap<string, ApiPlace>> {
  if (cache) return Promise.resolve(cache);
  pending ??= getMyCommunityPlaces()
    .then((list) => {
      cache = new Map(list.map((p) => [p.id, p]));
      return cache;
    })
    .finally(() => {
      pending = null;
    });
  return pending;
}

export function useCommunityPlaces(): ReadonlyMap<string, ApiPlace> | null {
  const [places, setPlaces] = useState<ReadonlyMap<string, ApiPlace> | null>(cache);
  useEffect(() => {
    if (cache) return;
    let alive = true;
    load()
      .then((m) => alive && setPlaces(m))
      .catch(() => alive && setPlaces(new Map()));
    return () => {
      alive = false;
    };
  }, []);
  return places;
}
