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

/** 동네 선택 단계 (E_073). 위에서 아래로 좁힌다 */
export type RegionLevel = "SIDO" | "SIGUNGU" | "GU" | "DONG";

/**
 * 고른 상위 단계. 위에서 아래로 채운다 — 위를 비우고 아래만 채우면 400이다.
 * 비어 있으면 "전체"다.
 */
export type RegionScope = {
  sido?: string;
  sigungu?: string;
  gu?: string;
  dong?: string;
};

export type ApiRegionOptions = { level: RegionLevel; items: string[] };

/**
 * E_073 동네 선택 단계 옵션. **등록된** 활성 동네의 공개 주소에서 모은다.
 *
 * 전국 행정동 목록이 아니다. 아직 아무도 인증하지 않은 행정동은 나오지 않고,
 * 그 동네로 처음 들어가는 길은 이름 검색(E_009)이다.
 * `GU`는 "수원시 영통구"처럼 시 아래 구가 있는 지역만 값이 있고, 없으면 빈 배열이다.
 *
 * `hasMembers`는 가입자가 있는 동네만 센다. 검색만 해도 행정동이 동네로 등록되므로
 * 아무도 없는 동네가 섞인다.
 */
export function getRegionOptions(
  level: RegionLevel,
  scope: RegionScope = {},
  hasMembers = false,
): Promise<ApiRegionOptions> {
  const { sido, sigungu, gu } = scope;
  return api<ApiRegionOptions>("/communities/regions", {
    query: { level, sido, sigungu, gu, hasMembers: hasMembers ? "true" : undefined },
  });
}

/**
 * E_009 동네 검색. 이름·공개 주소 부분 검색. keyword를 비우면 활성 동네 목록.
 * `scope`를 주면 그 지역 안으로 좁히고, keyword와 함께 주면 범위 안에서 이름을 찾는다.
 * `hasMembers`의 뜻은 `getRegionOptions`와 같다.
 */
export function searchCommunities(
  keyword?: string,
  page = 0,
  size = 20,
  scope: RegionScope = {},
  hasMembers = false,
): Promise<ApiCommunityPage> {
  const { sido, sigungu, gu, dong } = scope;
  return api<ApiCommunityPage>("/communities", {
    query: {
      keyword, sido, sigungu, gu, dong,
      hasMembers: hasMembers ? "true" : undefined,
      page, size,
    },
  });
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
