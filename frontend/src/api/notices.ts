import { api } from "@/api/client";

/** 공지 API (E_063~E_068). 필드명은 API 명세서 그대로 */

export type ApiNotice = {
  id: string;
  communityId: string;
  title: string;
  body: string;
  pinned: boolean;
  version: number;
  createdAt: string;
};

export type ApiNoticePage = {
  items: ApiNotice[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
};

/** E_063 동네 공지 목록. 유효 소속 회원 또는 ADMIN. 고정 먼저 최신순 */
export function getCommunityNotices(communityId: string, page = 0, size = 20): Promise<ApiNoticePage> {
  return api<ApiNoticePage>(`/communities/${communityId}/notices`, { query: { page, size } });
}

/** E_064 공지 상세 */
export function getNotice(noticeId: string): Promise<ApiNotice> {
  return api<ApiNotice>(`/notices/${noticeId}`);
}

/** E_065 관리자 공지 목록. 동네 필터는 선택 */
export function getAdminNotices(communityId?: string, page = 0, size = 20): Promise<ApiNoticePage> {
  return api<ApiNoticePage>("/admin/notices", { query: { communityId, page, size } });
}

/** E_066 공지 작성. 본문은 일반 텍스트로 렌더링하며 임의 HTML을 실행하지 않는다 */
export function createNotice(body: {
  communityId: string;
  title: string;
  body: string;
  pinned: boolean;
}): Promise<ApiNotice> {
  return api<ApiNotice>("/admin/notices", { method: "POST", json: body });
}

/** E_067 공지 수정·고정. version 검사 후 전체 필드 교체 */
export function updateNotice(
  noticeId: string,
  body: { expectedVersion: number; title: string; body: string; pinned: boolean },
): Promise<ApiNotice> {
  return api<ApiNotice>(`/admin/notices/${noticeId}`, { method: "PUT", json: body });
}

/** E_068 공지 삭제. version 검사 후 논리 삭제 */
export function deleteNotice(noticeId: string, expectedVersion: number): Promise<void> {
  return api<void>(`/admin/notices/${noticeId}`, { method: "DELETE", query: { expectedVersion } });
}
