/** 물건 도메인 타입 — ERD item 그룹 */
/** 물건 도메인 타입·목데이터 — API 연동 시 목 부분 교체 */
export type CategoryId =
  | "tool"
  | "camp"
  | "travel"
  | "baby"
  | "music"
  | "sports"
  | "kitchen"
  | "clean"
  | "life"
  | "etc";

export type Category = {
  id: CategoryId;
  label: string;
  icon: string;
};

export type Trust = {
  kept: number;
  total: number;
  done: number;
  lent?: number;
  late?: string;
  since?: string;
};

export type ItemAvailability = "ok" | "partial" | "none";

export type Item = {
  id: number;
  title: string;
  cat: CategoryId;
  owner: string;
  place: string;
  dist: string;
  avail: ItemAvailability;
  availText?: string;
  trust: Trust;
  ago: string;
  photos: number;
};
