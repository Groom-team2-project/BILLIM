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
  /** 목데이터는 number, API 데이터는 ID 문자열 */
  id: number | string;
  title: string;
  cat: CategoryId;
  owner: string;
  place: string;
  dist: string;
  avail: ItemAvailability;
  availText?: string;
  /** 거래 신뢰 지표 — C_008 연동 전에는 API 데이터에 없음 */
  trust?: Trust;
  ago: string;
  /** 사진 장수 — 목록 API에는 없음 */
  photos?: number;
  /** 대표 사진 주소 (API 데이터) */
  thumbnailUrl?: string;
};
