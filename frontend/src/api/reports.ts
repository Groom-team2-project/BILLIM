import { api } from "@/api/client";
import type { ApiMemberSummary } from "@/api/members";

/** 신고·제재 API (E_055~E_062). 필드명은 API 명세서 그대로 */

export type ReportTargetType = "MEMBER" | "ITEM" | "MESSAGE";

/** 신고 사유 6종 (명세 0.5) */
export type ReportReasonCode =
  | "MONEY_REQUEST"
  | "ITEM_MISMATCH"
  | "NO_SHOW"
  | "ABUSE"
  | "FRAUD"
  | "OTHER";

/** 신고 조치 5종 (정책 및 상태 5 제재 조치) */
export type ReportActionType = "WARN" | "SUSPEND_7D" | "SUSPEND_30D" | "BAN" | "DISMISS";

export type ApiReport = {
  id: string;
  targetType: ReportTargetType;
  targetId: string;
  targetMember: ApiMemberSummary;
  reasonCode: ReportReasonCode;
  detail?: string;
  status: "PENDING" | "RESOLVED";
  publicReason?: string;
  action?: ReportActionType;
  version: number;
  createdAt: string;
};

export type ApiReportPage = {
  items: ApiReport[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
};

/** 신고자 정보와 internalNote는 관리자 응답에만 담긴다 (API 명세 13.2) */
export type ApiAdminReport = {
  report: ApiReport;
  reporter: ApiMemberSummary;
  communityId: string;
  evidenceText?: string;
  internalNote?: string;
};

export type ApiAdminReportPage = {
  items: ApiAdminReport[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
};

export type ApiSanction = {
  id: string;
  memberId: string;
  kind: "SUSPEND" | "BAN";
  startsAt: string;
  endsAt?: string;
  revokedAt?: string;
  isEffective: boolean;
};

export type ApiSanctionPage = {
  items: ApiSanction[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
};

/**
 * 내가 받은 운영 조치 한 건 (E_072).
 *
 * 경고(WARN)는 제재 행이 없어 kind·startsAt·endsAt이 없고 isEffective는 false다.
 * 신고자·운영 메모·신고 사유 코드·대상 거래는 내려오지 않는다 — 신고자 비공개 때문이다.
 * (도메인 가이드 결정 '조치 사유를 누구에게 보여줄지')
 */
export type ApiMySanction = {
  id: string;
  action: Exclude<ReportActionType, "DISMISS">;
  /** 운영자가 쓴 최소 사유. 화면에 그대로 보여 준다 */
  publicReason: string;
  decidedAt: string;
  kind?: "SUSPEND" | "BAN";
  startsAt?: string;
  endsAt?: string;
  revokedAt?: string;
  isEffective: boolean;
};

export type ApiMySanctionPage = {
  items: ApiMySanction[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
};

/** E_055 신고 접수. targetId는 대상 종류에 맞는 ID이고 대상 회원은 서버가 계산한다 */
export function createReport(body: {
  targetType: ReportTargetType;
  targetId: string;
  reasonCode: ReportReasonCode;
  detail?: string;
}): Promise<ApiReport> {
  return api<ApiReport>("/reports", { method: "POST", json: body });
}

/** 채팅 메시지 신고 (E_055의 MESSAGE 대상). 채팅 화면에서 쓴다 */
export function reportMessage(
  messageId: string,
  reasonCode: ReportReasonCode,
  detail?: string,
): Promise<ApiReport> {
  return createReport({ targetType: "MESSAGE", targetId: messageId, reasonCode, detail });
}

/** E_056 내 신고 목록 */
export function getMyReports(page = 0, size = 20): Promise<ApiReportPage> {
  return api<ApiReportPage>("/members/me/reports", { query: { page, size } });
}

/** E_057 내 신고 결과 상세. 신고자만 */
export function getMyReport(reportId: string): Promise<ApiReport> {
  return api<ApiReport>(`/reports/${reportId}`);
}

/** E_058 운영 신고 목록. ADMIN만 */
export function getAdminReports(
  status?: "PENDING" | "RESOLVED",
  communityId?: string,
  page = 0,
  size = 20,
): Promise<ApiAdminReportPage> {
  return api<ApiAdminReportPage>("/admin/reports", { query: { status, communityId, page, size } });
}

/** E_059 신고 증거와 처리 상세. ADMIN만 */
export function getAdminReport(reportId: string): Promise<ApiAdminReport> {
  return api<ApiAdminReport>(`/admin/reports/${reportId}`);
}

/** E_060 신고 처리·제재 결정. 처리 이력·제재·알림이 한 트랜잭션에서 저장된다 */
export function resolveReport(
  reportId: string,
  body: {
    expectedVersion: number;
    action: ReportActionType;
    publicReason: string;
    internalNote?: string;
  },
): Promise<ApiAdminReport> {
  return api<ApiAdminReport>(`/admin/reports/${reportId}/resolve`, { method: "POST", json: body });
}

/**
 * E_072 내가 받은 운영 조치 내역. 본인만. 최근 조치가 먼저 온다.
 *
 * 처리 결과 알림이 "자세한 내용은 내 프로필에서 확인해 주세요"로 안내하는 그 화면이 쓴다.
 * 제재 중이어도 호출된다 — 제한된 이유를 모르면 이의를 제기할 수 없다.
 */
export function getMySanctions(page = 0, size = 20): Promise<ApiMySanctionPage> {
  return api<ApiMySanctionPage>("/members/me/sanctions", { query: { page, size } });
}

/** E_061 제재 이력 조회. 만료·해제 포함 */
export function getSanctions(memberId?: string, page = 0, size = 20): Promise<ApiSanctionPage> {
  return api<ApiSanctionPage>("/admin/sanctions", { query: { memberId, page, size } });
}

/** E_062 제재 해제. 사유 필수 */
export function revokeSanction(sanctionId: string, reason: string): Promise<ApiSanction> {
  return api<ApiSanction>(`/admin/sanctions/${sanctionId}/revoke`, {
    method: "POST",
    json: { reason },
  });
}
