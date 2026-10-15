package com.billim.domain.community.service;

import java.util.Optional;

/**
 * 소속 판정 창구. 커뮤니티 도메인이 다른 도메인에 공개하는 읽기 계약이다. (도메인 가이드 C06)
 *
 * <p>각 도메인은 자기 API에서 이 결과로 권한을 검사한다. 판정 규칙(활성 + 인증 만료 전)이
 * 바뀌면 이 구현만 고치면 되고 호출 측은 그대로 둔다.
 *
 * <p>연동 지점
 * <ul>
 *   <li>물건: {@code ItemAccessPolicy.findActiveCommunityId()}가 현재 503을 던진다.
 *       이 인터페이스로 교체하면 등록·상세·검색이 열린다. (B 담당 합의 후)</li>
 *   <li>대여·채팅: 요청·방 생성 시 같은 동네인지 확인</li>
 * </ul>
 */
public interface CommunityMembershipQuery {

    /**
     * 회원의 유효한 활성 동네 ID. 유효 소속이 없으면 빈 Optional.
     * 인증이 만료된 소속은 유효하지 않다.
     */
    Optional<Long> findActiveCommunityId(long memberId);

    /** 이 회원이 해당 동네의 유효 소속인지 */
    boolean hasValidMembership(long memberId, long communityId);
}
