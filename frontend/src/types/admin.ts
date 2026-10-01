/** 관리자(안전·운영) 도메인 타입 */
/** 커뮤니티별 운영 지표 목데이터 — 프로토타입 예시 수치 */
export type CommStats = {
  items: number;
  active: number;
  newItems: number;
  newUsers: number;
  rate: number;
  week: [string, number][];
  cats: [string, number][];
  popular: [string, number][];
};

export type Report = {
  id: number;
  name: string;
  reason: string;
  date: string;
  community: string;
  status: "pending" | "done";
  action: string | null;
};

export type Notice = {
  id: number;
  title: string;
  date: string;
  pinned: boolean;
};
