package com.billim.domain.item.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 대여 정보 조회 계약. 구현은 대여 담당(C) 영역.
 * "진행 중"은 REQUESTED·APPROVED·ACTIVE.
 * TODO(C): rentals 기반 구현체 연결.
 */
public interface RentalPort {

    /** 진행 중인 대여 기간들 (수정 시 가능 기간이 모두 포함해야 함) */
    List<DateRange> findOpenRentalRanges(long itemId);

    /** 진행 중인 대여가 있는지 (삭제 제한) */
    boolean hasOpenRentals(long itemId);

    /** 선택 기간과 점유·연체 규칙상 충돌하는 물건 ID (검색 제외용). C의 겹침·연체 규칙 사용 */
    Set<Long> findUnavailableItemIds(long communityId, LocalDate startDate, LocalDate endDate);

    /** 회원이 해당 물건의 기존 거래 당사자(소유자·요청자)인지 (사진 읽기 권한) */
    boolean isRentalParticipant(long itemId, long memberId);

    /** 물건별 현재 REQUESTED 수 (내 물건 목록용) */
    Map<Long, Integer> countPendingRequests(Collection<Long> itemIds);

    record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
