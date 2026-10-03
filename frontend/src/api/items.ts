import { api } from "@/api/client";
import type { CreateItemBody } from "@/utils/registerForm";

/** 물건·카테고리·사진 API (B_013~B_023). 필드명은 API 명세서 그대로 */

export type ApiCategory = { id: string; code: string; name: string; sortOrder: number };
export type ApiPlace = { id: string; communityId: string; name: string; latitude: number; longitude: number; guide?: string };
/** 물건 응답의 소유자·장소. 회원(A)·동네(E) 도메인 연동 전에는 id 외 값이 내려오지 않는다 */
export type ApiItemMember = { id: string; displayName?: string; joinedAt?: string };
export type ApiItemPlace = { id: string; communityId: string; name?: string; latitude?: number; longitude?: number; guide?: string };
export type ApiMedia = {
  id: string;
  mimeType: string;
  byteSize: number;
  width: number;
  height: number;
  contentUrl: string;
  status: "TEMP" | "ATTACHED";
  expiresAt?: string;
};
export type ApiImage = { mediaId: string; sortOrder: number; contentUrl: string };
export type ItemVisibility = "PUBLIC" | "HIDDEN" | "DELETED";

export type ApiItemSummary = {
  id: string;
  title: string;
  category: ApiCategory;
  owner: ApiItemMember;
  place: ApiItemPlace;
  thumbnailUrl?: string;
  visibility: ItemVisibility;
  distanceMeters?: number;
  distanceBasis?: "COMMUNITY_CENTER";
  availableForRange?: boolean;
  createdAt: string;
  pendingRequestCount?: number;
  version: number;
};
export type ApiItemPage = { items: ApiItemSummary[]; page: number; size: number; totalElements: number; hasNext: boolean };
export type ApiItemDetail = {
  id: string;
  title: string;
  description?: string;
  owner: ApiItemMember;
  communityId: string;
  category: ApiCategory;
  place: ApiItemPlace;
  images: ApiImage[];
  availableStartDate: string;
  availableEndDate: string;
  visibility: ItemVisibility;
  distanceMeters?: number;
  distanceBasis?: "COMMUNITY_CENTER";
  version: number;
  allowedActions: string[];
  createdAt: string;
};

export type { CreateItemBody } from "@/utils/registerForm";
export type UpdateItemBody = CreateItemBody & { expectedVersion: number };

export type ItemSearchParams = {
  keyword?: string;
  categoryId?: string;
  startDate?: string;
  endDate?: string;
  placeId?: string;
  sort?: "LATEST" | "NEAREST";
  page?: number;
  size?: number;
};

export const getCategories = () => api<{ items: ApiCategory[] }>("/categories").then((r) => r.items);

export function uploadMedia(file: File, idempotencyKey?: string) {
  const form = new FormData();
  form.append("file", file);
  return api<ApiMedia>("/media", { method: "POST", form, idempotencyKey });
}
export const deleteMedia = (mediaId: string) => api<void>(`/media/${mediaId}`, { method: "DELETE" });

export const searchItems = (params: ItemSearchParams) => api<ApiItemPage>("/items", { query: { ...params } });
export const getItem = (itemId: string) => api<ApiItemDetail>(`/items/${itemId}`);
export const createItem = (body: CreateItemBody, idempotencyKey?: string) =>
  api<ApiItemDetail>("/items", { method: "POST", json: body, idempotencyKey });
export const updateItem = (itemId: string, body: UpdateItemBody) =>
  api<ApiItemDetail>(`/items/${itemId}`, { method: "PUT", json: body });
export const changeItemVisibility = (itemId: string, visibility: "PUBLIC" | "HIDDEN", expectedVersion: number) =>
  api<ApiItemDetail>(`/items/${itemId}/visibility`, { method: "PATCH", json: { visibility, expectedVersion } });
export const deleteItem = (itemId: string, expectedVersion: number) =>
  api<void>(`/items/${itemId}`, { method: "DELETE", query: { expectedVersion } });

/**
 * 등록 화면의 거래 장소 목록.
 * TODO(A·E): 다른 담당 API(A_005 내 소속, E_012 공용 장소)를 읽기만 한다. 응답 형식은 명세 기준.
 */
export async function getMyCommunityPlaces(): Promise<ApiPlace[]> {
  const me = await api<{ activeMembership?: { communityId: string; isValid: boolean } }>("/members/me");
  const m = me.activeMembership;
  if (!m || !m.isValid) return [];
  return api<{ items: ApiPlace[] }>(`/communities/${m.communityId}/places`).then((r) => r.items);
}
