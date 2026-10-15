import { api } from "@/api/client";
import type { ApiMemberSummary } from "@/api/members";

/** 차단 API (E_052~E_054). 필드명은 API 명세서 그대로 */

export type ApiBlock = { member: ApiMemberSummary; blockedAt: string };

export type ApiBlockPage = {
  items: ApiBlock[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
};

/** E_052 내 차단 목록. 내가 차단한 방향만 내려온다 */
export function getMyBlocks(page = 0, size = 20): Promise<ApiBlockPage> {
  return api<ApiBlockPage>("/members/me/blocks", { query: { page, size } });
}

/** E_053 이웃 차단. 이미 차단했으면 그대로 성공 */
export function blockMember(memberId: string): Promise<void> {
  return api<void>(`/members/me/blocks/${memberId}`, { method: "PUT" });
}

/** E_054 내 차단 해제. 없는 관계도 성공. 상대가 나를 차단한 상태면 제한은 유지된다 */
export function unblockMember(memberId: string): Promise<void> {
  return api<void>(`/members/me/blocks/${memberId}`, { method: "DELETE" });
}
