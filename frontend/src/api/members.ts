import { api } from "@/api/client";

/** 회원 API (A_005~A_007). 필드명은 API 명세서 그대로 */

export type ApiMemberSummary = { id: string; displayName: string; joinedAt: string };

/** activeMembership은 동네(E) 도메인 연동 전이라 내려오지 않는다 */
export type ApiMyProfile = {
  member: ApiMemberSummary;
  role: "USER" | "ADMIN";
  status: "ACTIVE" | "WITHDRAWN";
  isRestricted: boolean;
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
