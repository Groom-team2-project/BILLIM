package com.billim.domain.rental.service;

import com.billim.domain.rental.dto.CreateRentalRequest;
import com.billim.domain.rental.dto.RentalDetailStatusResponse;
import com.billim.domain.rental.entity.Rental;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** DB 변경 보류 중인 멱등 기록·outbox 연동 경계. 성공 기록을 메모리로 대체하지 않음. */
@Component
public class RentalRequestEffects {

    /** 회원 잠금 아래 키·본문 일치 검사. 유효한 최초 성공 응답만 반환. */
    public Optional<RentalDetailStatusResponse> findReplay(long memberId, String key,
                                                          CreateRentalRequest request, Instant now) {
        throw unavailable();
    }

    /** 거래·이력과 동일 트랜잭션으로 사건과 24시간 유효한 성공 응답 저장 필요. */
    public void recordSuccess(long memberId, String key, CreateRentalRequest request,
                              Rental rental, RentalDetailStatusResponse response, Instant now) {
        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                "대여 멱등 기록·이벤트 저장 기반 연동 전이라 처리할 수 없습니다.");
    }
}
