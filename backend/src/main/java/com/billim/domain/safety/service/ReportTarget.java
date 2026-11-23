package com.billim.domain.safety.service;

/**
 * 신고 대상을 서버가 해석한 결과. (API 명세 12.4 — "targetId를 서버 해석하고 targetMember 결정")
 *
 * @param targetMemberId 처리 대상 회원. 물건은 소유자, 메시지는 보낸 사람
 * @param targetRef      대상 종류별 참조 ID. MEMBER는 null
 * @param communityId    신고 맥락의 커뮤니티
 * @param snapshot       접수 시점의 최소 증거
 */
public record ReportTarget(
        long targetMemberId,
        Long targetRef,
        long communityId,
        ReportSnapshot snapshot
) {

    /**
     * reports.target_snapshot에 저장하는 최소 증거. 관리자에게만 공개한다. (ERD 4.7)
     * 개인 좌표·연락처는 담지 않고, 대상 식별에 필요한 표시 정보만 남긴다.
     *
     * @param targetLabel  대상 표시 이름. 회원은 표시 이름, 물건은 제목
     * @param evidenceText 신고 맥락의 본문. 메시지 신고일 때의 메시지 내용 등
     */
    public record ReportSnapshot(String targetLabel, String evidenceText) {

        public static final int EVIDENCE_MAX = 3000;

        public static ReportSnapshot of(String targetLabel, String evidenceText) {
            return new ReportSnapshot(targetLabel, truncate(evidenceText));
        }

        /** 증거 본문은 응답 상한(AdminReport.evidenceText 3000자)에 맞춰 자른다 */
        private static String truncate(String text) {
            if (text == null) {
                return null;
            }
            return text.length() <= EVIDENCE_MAX ? text : text.substring(0, EVIDENCE_MAX);
        }
    }
}
