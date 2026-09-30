/** 동네 후보 목데이터 — 현재 위치(새솔마을) 기준 근처. API 연동 시 교체 */
export const NEARBY_NEIGHBORHOODS: { name: string; region: string; neighbors: number }[] = [
  { name: "새솔마을 4단지", region: "대전 유성구", neighbors: 86 },
  { name: "새솔마을 6단지", region: "대전 유성구", neighbors: 64 },
  { name: "도안신도시 7단지", region: "대전 서구", neighbors: 152 },
  { name: "한빛초 학부모방", region: "대전 유성구", neighbors: 41 },
];

export const RECENT_NEIGHBORHOODS = [{ name: "도안신도시 7단지", region: "대전 서구" }];
