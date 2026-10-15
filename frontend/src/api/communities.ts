import { api } from "@/api/client";

/** 동네·공용 장소 API (E_009~E_012, E_071). 필드명은 API 명세서 그대로 */

export type ApiCommunity = {
  id: string;
  name: string;
  displayAddress: string;
  centerLatitude: number;
  centerLongitude: number;
  verificationRadiusM: number;
  verifiedMemberCount: number;
};

export type ApiCommunityPage = {
  items: ApiCommunity[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
};

/** truncated면 후보가 더 있다는 뜻 — 이름 검색으로 보완한다 (E_071) */
export type ApiNearbyCommunities = { items: ApiCommunity[]; truncated: boolean };

export type ApiCommunityPlace = {
  id: string;
  communityId: string;
  name: string;
  latitude: number;
  longitude: number;
  guide?: string;
};

export type ApiMembership = {
  id: string;
  communityId: string;
  communityName: string;
  status: "ACTIVE" | "LEFT";
  verifiedAt: string;
  verificationExpiresAt: string;
  isValid: boolean;
};

/** 위치 인증·주변 찾기 요청 본문. 좌표는 이 요청에서만 쓰이고 서버에 저장되지 않는다 */
export type VerifyLocationBody = {
  latitude: number;
  longitude: number;
  accuracyMeters: number;
};

/** E_009 동네 이름 검색. keyword를 비우면 활성 동네 목록 */
export function searchCommunities(keyword?: string, page = 0, size = 20): Promise<ApiCommunityPage> {
  return api<ApiCommunityPage>("/communities", { query: { keyword, page, size } });
}

/** E_010 동네 정보 조회. 비활성 동네는 404 */
export function getCommunity(communityId: string): Promise<ApiCommunity> {
  return api<ApiCommunity>(`/communities/${communityId}`);
}

/** E_071 현재 위치로 주변 동네 찾기. 이 조회 자체는 소속 인증이 아니다 */
export function findNearbyCommunities(body: VerifyLocationBody): Promise<ApiNearbyCommunities> {
  return api<ApiNearbyCommunities>("/communities/nearby", { method: "POST", json: body });
}

/**
 * E_011 위치 인증 및 활성 동네 선택. 최초·재인증 모두 200.
 * 범위 밖은 403 LOCATION_OUTSIDE, 정확도 초과는 422 LOCATION_INACCURATE.
 */
export function verifyLocation(communityId: string, body: VerifyLocationBody): Promise<ApiMembership> {
  return api<ApiMembership>(`/communities/${communityId}/verifications`, { method: "POST", json: body });
}

/** E_012 공용 거래 장소 목록. 활성 장소만 이름순 */
export function getCommunityPlaces(communityId: string): Promise<ApiCommunityPlace[]> {
  return api<{ items: ApiCommunityPlace[] }>(`/communities/${communityId}/places`).then((r) => r.items);
}
