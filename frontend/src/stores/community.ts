import { useSyncExternalStore } from "react";

/** 내 동네 — 최대 2개, 위치 인증을 마쳐야 거래 가능 */
export type Community = {
  name: string;
  region: string;
  verified: boolean;
};

export const MAX_COMMUNITIES = 2;

let communities: Community[] = [{ name: "새솔마을 5단지", region: "대전 유성구", verified: true }];
let active = "새솔마을 5단지";

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

export function useCommunities(): Community[] {
  return useSyncExternalStore(subscribe, () => communities);
}

export function useActiveCommunity(): string {
  return useSyncExternalStore(subscribe, () => active);
}

export function hasCommunity(name: string) {
  return communities.some((c) => c.name === name);
}

/** 인증 완료된 동네 추가 — 추가한 동네를 활성으로 전환 */
export function addCommunity(name: string, region: string) {
  if (communities.length >= MAX_COMMUNITIES || hasCommunity(name)) return;
  communities = [...communities, { name, region, verified: true }];
  active = name;
  emit();
}

/** 동네 삭제 — 최소 1개는 유지 */
export function removeCommunity(name: string) {
  if (communities.length <= 1) return;
  communities = communities.filter((c) => c.name !== name);
  if (active === name) active = communities[0].name;
  emit();
}

export function setActiveCommunity(name: string) {
  if (!hasCommunity(name)) return;
  active = name;
  emit();
}
