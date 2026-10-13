package com.billim.domain.rental.service;

import com.billim.domain.item.entity.Item;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.item.service.ItemAccessPolicy;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/** 동네·차단·제재·장소 연동 경계. 미연동 상태에서 검증 우회 금지. */
@Component
public class RentalRequestPolicy {
    private final ItemAccessPolicy itemAccessPolicy;

    public RentalRequestPolicy(ItemAccessPolicy itemAccessPolicy) {
        this.itemAccessPolicy = itemAccessPolicy;
    }

    public void requireViewable(Item item, long memberId) {
        if (!itemAccessPolicy.canView(item, memberId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // 동네·차단 연동 완료 전 재전송도 조회 권한을 확정할 수 없음
        throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "대여 권한 연동 전이라 처리할 수 없습니다.");
    }

    /** 양측 제재·유효 소속·차단 및 공용 장소 검증 후 실제 장소명 반환 필요. */
    public String requireRequestAllowed(Item item, long memberId) {
        throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "동네·제재·장소 연동 전이라 처리할 수 없습니다.");
    }

    /** 물건의 수동 대여 상태 저장 기반 연결 전 요청 허용 금지. */
    public void requireAvailable(Item item) {
        throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "물건 대여 상태 연동 전이라 처리할 수 없습니다.");
    }
    /** 승인 시 양측의 현재 소속·제재·차단 재검사. 기존 거래 정리 명령에는 미적용. */
    public void requireApprovalAllowed(Item item, Rental rental) {
        throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "대여 승인 권한 연동 전이라 처리할 수 없습니다.");
    }

}
