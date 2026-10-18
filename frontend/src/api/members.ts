import { api } from "@/api/client";
import type { ApiItemPage } from "@/api/items";

/** 회원 API (A_005~A_007). 필드명은 API 명세서 그대로 */

export type ApiMemberSummary = { id: string; displayName: string; joinedAt: string };

/** 소속·인증 시점 (API 명세 Membership) */
export type ApiMembership = {
  id: string;
  communityId: string;
  communityName: string;
  status: "ACTIVE" | "LEFT";
  verifiedAt: string;
  verificationExpiresAt: string;
  isValid: boolean;
};

/** activeMembership 생략은 유효 소속 없음이며 onboardingRequired와 같은 조건 */
export type ApiMyProfile = {
  member: ApiMemberSummary;
  role: "USER" | "ADMIN";
  status: "ACTIVE" | "WITHDRAWN";
  isRestricted: boolean;
  activeMembership?: ApiMembership;
  onboardingRequired: boolean;
  version: number;
};

export function getMyProfile(): Promise<ApiMyProfile> {
  return api<ApiMyProfile>("/members/me");
}

export function updateDisplayName(displayName: string, expectedVersion: number): Promise<ApiMyProfile> {
  return api<ApiMyProfile>("/members/me", {
    method: "PATCH",
    json: { displayName, expectedVersion },
  });
}

/** 이웃 공개 프로필 (API 명세 PublicProfile). 소셜 식별자·연락처 미포함 */
export type ApiPublicProfile = {
  member: ApiMemberSummary;
  communityName?: string;
  isVerified: boolean;
  blockedByMe: boolean;
};

/** 거래 신뢰 지표 (API 명세 TrustMetrics) */
export type ApiTrustMetrics = {
  completedCount: number;
  lentCount: number;
  returnPromiseKept: number;
  returnPromiseTotal: number;
  basis: string;
};

/** A_007. 같은 유효 동네 또는 기존 거래 당사자만 조회 가능하며 그 밖은 404 */
export function getPublicProfile(memberId: string): Promise<ApiPublicProfile> {
  return api<ApiPublicProfile>(`/members/${memberId}`);
}

/** C_008 신뢰 지표 — 대여 담당 API. 공개 프로필과 같은 열람 권한 */
export function getMemberTrust(memberId: string): Promise<ApiTrustMetrics> {
  return api<ApiTrustMetrics>(`/members/${memberId}/trust`);
}

/** B_025 이웃 공개 물건 목록 — 물건 담당 API. 내 유효 동네의 PUBLIC만 */
export function getNeighborItems(memberId: string, page = 0, size = 20): Promise<ApiItemPage> {
  return api<ApiItemPage>(`/members/${memberId}/items`, { query: { page, size } });
}

/** B_024 내 물건 목록 — 물건 담당 API. 관리 상태·대기 요청 수 포함 */
export function getMyItems(page = 0, size = 20): Promise<ApiItemPage> {
  return api<ApiItemPage>("/members/me/items", { query: { page, size } });
}
