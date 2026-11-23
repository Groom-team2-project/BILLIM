import type { ApiRental, ApiRentalPage, ApiRentalQuery, RentalRole } from "@/types/rental-api";
import type { RentalFilter } from "@/utils/rentalView";

export const RENTAL_PAGE_SIZE = 20;

type Stream = { query: ApiRentalQuery; page: number; hasNext: boolean; items: ApiRental[] };
export type RentalCursor = { streams: Stream[] };
export type RentalPageFetcher = (role: RentalRole, page: number, query: ApiRentalQuery) => Promise<ApiRentalPage>;

export function rentalCursor(filter: RentalFilter): RentalCursor {
  const queries: ApiRentalQuery[] = filter === "done"
    ? ["RETURNED", "REJECTED", "CANCELED", "EXPIRED"].map((status) => ({ status: status as ApiRental["status"] }))
    : filter === "all" ? [{}]
    : filter === "overdue" ? [{ status: "ACTIVE", overdueOnly: true }]
    : [{ status: filter.toUpperCase() as ApiRental["status"] }];
  return { streams: queries.map((query) => ({ query, page: 0, hasNext: true, items: [] })) };
}

/** 서버의 생성 시각 DESC·숫자 ID DESC 순서 유지. 마이크로초도 비교. */
export function compareRentals(a: ApiRental, b: ApiRental) {
  const time = Date.parse(b.createdAt) - Date.parse(a.createdAt);
  if (time) return time;
  const fraction = (date: string) => (date.match(/\.(\d+)/)?.[1] ?? "").padEnd(9, "0");
  const precision = fraction(b.createdAt).localeCompare(fraction(a.createdAt));
  if (precision) return precision;
  return BigInt(a.id) > BigInt(b.id) ? -1 : BigInt(a.id) < BigInt(b.id) ? 1 : 0;
}

/** 완료 4개 상태의 페이지를 병합. 실패 시 원본 커서를 보존해 같은 페이지 재시도. */
export async function loadRentalPage(role: RentalRole, filter: RentalFilter, cursor: RentalCursor, fetchPage: RentalPageFetcher) {
  const streams = cursor.streams.map((stream) => ({ ...stream, items: [...stream.items] }));
  const items: ApiRental[] = [];
  while (items.length < RENTAL_PAGE_SIZE) {
    await Promise.all(streams.filter((s) => s.items.length === 0 && s.hasNext).map(async (s) => {
      const page = await fetchPage(role, s.page, { ...s.query, size: RENTAL_PAGE_SIZE });
      s.items = page.items;
      s.hasNext = page.hasNext;
      s.page++;
      if (!page.items.length && page.hasNext) throw new Error("비어 있는 대여 페이지의 다음 페이지 정보 불일치");
    }));
    const next = streams.filter((s) => s.items.length).sort((a, b) => compareRentals(a.items[0], b.items[0]))[0];
    if (!next) break;
    const rental = next.items.shift()!;
    // ACTIVE는 연체를 포함하는 서버 상태. 화면의 대여 중 필터에서는 연체 제외.
    if (filter !== "active" || !rental.overdue) items.push(rental);
  }
  return { items, cursor: { streams }, hasNext: streams.some((s) => s.items.length > 0 || s.hasNext) };
}
