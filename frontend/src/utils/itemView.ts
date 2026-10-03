/** 물건 API 응답 → 기존 화면 모델 변환 (순수 함수, tests/에서 node로 검증) */
import type { CategoryId, Item } from "@/types/item";

/** API 명세 ItemSummary 중 화면에 쓰는 필드 */
export type SummaryLike = {
  id: string;
  title: string;
  category: { code: string };
  owner: { displayName?: string };
  place: { name?: string };
  thumbnailUrl?: string;
  distanceMeters?: number;
  availableForRange?: boolean;
  createdAt: string;
};

const CATEGORY_ID_BY_CODE: Record<string, CategoryId> = {
  TOOL: "tool",
  CAMP: "camp",
  TRAVEL: "travel",
  BABY: "baby",
  MUSIC: "music",
  SPORT: "sports",
  KITCHEN: "kitchen",
  CLEAN: "clean",
  LIFE: "life",
  OTHER: "etc",
};

/** 서버 카테고리 코드 → 기존 아이콘·라벨 키 */
export function toCategoryId(code: string): CategoryId {
  return CATEGORY_ID_BY_CODE[code] ?? "etc";
}

/** 소유자·장소 이름이 아직 내려오지 않을 때(회원·동네 연동 전) 화면에 쓰는 이름 */
export const UNKNOWN_OWNER = "이웃";
export const UNKNOWN_PLACE = "거래 장소";

/** 거리(m) 표시: 1km 미만은 m, 이상은 소수 첫째 자리 km */
export function formatDistance(meters: number): string {
  if (meters < 1000) return `${Math.max(0, Math.round(meters))}m`;
  return `${(meters / 1000).toFixed(1)}km`;
}

/** 등록 시각 상대 표시 */
export function formatAgo(iso: string, now: Date = new Date()): string {
  const t = new Date(iso);
  const diffMin = Math.floor((now.getTime() - t.getTime()) / 60000);
  if (diffMin < 1) return "방금 전";
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간 전`;
  const days = Math.floor(diffHour / 24);
  if (days < 2) return "어제";
  if (days < 30) return `${days}일 전`;
  return `${t.getMonth() + 1}월 ${t.getDate()}일`;
}

const DOW = ["일", "월", "화", "수", "목", "금", "토"];

/** "YYYY-MM-DD"(KST 달력 날짜) → 로컬 Date */
export function parseIsoDate(s: string): Date {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/** M/D(요일) */
export function formatMonthDay(d: Date): string {
  return `${d.getMonth() + 1}/${d.getDate()}(${DOW[d.getDay()]})`;
}

/** 가입 시각 → "2026년 9월" */
export function formatYearMonth(iso?: string): string {
  if (!iso) return "";
  const t = new Date(iso);
  return `${t.getFullYear()}년 ${t.getMonth() + 1}월`;
}

/** 목록 항목 → 기존 ItemCard 모델. 대여 가능 여부는 기간 검색 결과일 때만 서버 값 사용 */
export function toCardItem(s: SummaryLike, now: Date = new Date()): Item {
  return {
    id: s.id,
    title: s.title,
    cat: toCategoryId(s.category.code),
    owner: s.owner.displayName ?? UNKNOWN_OWNER,
    place: s.place.name ?? UNKNOWN_PLACE,
    dist: s.distanceMeters === undefined ? "" : formatDistance(s.distanceMeters),
    avail: s.availableForRange === false ? "none" : "ok",
    ago: formatAgo(s.createdAt, now),
    thumbnailUrl: s.thumbnailUrl,
  };
}

/** 검색 화면 조건 → API 쿼리 (API 명세 B_017). 기간은 시작·종료를 함께 보낸다 */
export type SearchForm = {
  keyword: string;
  categoryId: string | null;
  period: [Date, Date] | null;
  sort: "new" | "near";
  page: number;
  size: number;
};

export function toSearchQuery(f: SearchForm, toIso: (d: Date) => string) {
  const keyword = f.keyword.trim();
  return {
    ...(keyword ? { keyword } : {}),
    ...(f.categoryId ? { categoryId: f.categoryId } : {}),
    ...(f.period ? { startDate: toIso(f.period[0]), endDate: toIso(f.period[1]) } : {}),
    sort: f.sort === "near" ? ("NEAREST" as const) : ("LATEST" as const),
    page: f.page,
    size: f.size,
  };
}

/** 지도 핀: 결과를 공용 장소별로 묶고 좌표를 지도 영역(%) 안에 배치 */
export type PinSource = { place: { id: string; name?: string; latitude?: number; longitude?: number } };
export type MapPin = { id: string; label: string; x: number; y: number; n: number };

export function toMapPins(items: PinSource[]): MapPin[] {
  const byPlace = new Map<string, { label: string; lat: number; lng: number; n: number }>();
  for (const { place } of items) {
    // 좌표가 아직 내려오지 않는 장소(동네 연동 전)는 지도에 올리지 않는다
    if (place.latitude === undefined || place.longitude === undefined) continue;
    const cur = byPlace.get(place.id);
    if (cur) cur.n += 1;
    else byPlace.set(place.id, { label: place.name ?? UNKNOWN_PLACE, lat: place.latitude, lng: place.longitude, n: 1 });
  }
  const list = [...byPlace.entries()];
  const lats = list.map(([, p]) => p.lat);
  const lngs = list.map(([, p]) => p.lng);
  const [minLat, maxLat, minLng, maxLng] = [Math.min(...lats), Math.max(...lats), Math.min(...lngs), Math.max(...lngs)];
  const scale = (v: number, min: number, max: number) => (max === min ? 50 : 15 + ((v - min) / (max - min)) * 70);
  return list.map(([id, p]) => ({
    id,
    label: p.label,
    x: Math.round(scale(p.lng, minLng, maxLng)),
    y: Math.round(100 - scale(p.lat, minLat, maxLat)), // 북쪽이 위
    n: p.n,
  }));
}
