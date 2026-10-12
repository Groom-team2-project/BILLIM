package com.billim.domain.safety.service;

import com.billim.domain.safety.dto.SanctionPageResponse;
import com.billim.domain.safety.dto.SanctionResponse;
import com.billim.domain.safety.entity.Sanction;
import com.billim.domain.safety.repository.SanctionRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 제재 이력 조회와 해제 (API 명세 13.4, 13.5).
 *
 * <p>이 도메인은 제재를 결정·보관하고, 제재 상태로 요청을 막는 적용은 인증·회원 도메인이 한다.
 * 그 판정에 쓰는 입구가 {@link #isRestricted(long)}다. (ERD 4.7 — E 결정 / A 적용)
 */
@Service
@Transactional(readOnly = true)
public class SanctionService {

    private static final Logger log = LoggerFactory.getLogger(SanctionService.class);

    private final SanctionRepository sanctionRepository;
    private final MemberRowLock memberRowLock;
    private final Clock clock;

    public SanctionService(SanctionRepository sanctionRepository,
                           MemberRowLock memberRowLock,
                           Clock clock) {
        this.sanctionRepository = sanctionRepository;
        this.memberRowLock = memberRowLock;
        this.clock = clock;
    }

    /** 제재 이력. 만료·해제까지 모두 보여준다 (API 명세 13.4) */
    public SanctionPageResponse findSanctions(Long memberId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Sanction> sanctions = memberId == null
                ? sanctionRepository.findAllByOrderByStartsAtDescIdDesc(pageRequest)
                : sanctionRepository.findByMemberIdOrderByStartsAtDescIdDesc(memberId, pageRequest);

        Instant now = clock.instant();
        return new SanctionPageResponse(
                sanctions.getContent().stream().map(sanction -> SanctionResponse.of(sanction, now)).toList(),
                page, size, sanctions.getTotalElements(), sanctions.hasNext());
    }

    /**
     * 제재 해제 (API 명세 13.5).
     * 이미 해제된 제재는 기존 결과를 그대로 반환해 반복 호출에 안전하다.
     * 같은 회원에게 다른 유효 제재가 남아 있으면 회원은 계속 제한된다.
     */
    @Transactional
    public SanctionResponse revoke(long adminId, long sanctionId, String reason) {
        Sanction sanction = sanctionRepository.findById(sanctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 한 회원의 제재 변경을 직렬화한다. 잠금 순서는 신고 처리와 같게 회원 → 제재로 맞춘다
        memberRowLock.lock(sanction.getMemberId());
        Sanction locked = sanctionRepository.findByIdForUpdate(sanctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        boolean alreadyRevoked = locked.isRevoked();
        locked.revoke(adminId, reason, clock.instant());

        if (!alreadyRevoked) {
            log.info("제재 해제: sanctionId={} adminId={} memberId={}",
                    sanctionId, adminId, locked.getMemberId());
        }
        return SanctionResponse.of(locked, clock.instant());
    }

    /**
     * 지금 이 회원이 이용 제한 상태인지. 겹치는 제재는 하나라도 유효하면 제한이다.
     * 회원 프로필의 isRestricted 표시와 명령 API의 MEMBER_RESTRICTED 판정이 같은 값을 쓴다.
     */
    public boolean isRestricted(long memberId) {
        return sanctionRepository.existsEffective(memberId, clock.instant());
    }
}
