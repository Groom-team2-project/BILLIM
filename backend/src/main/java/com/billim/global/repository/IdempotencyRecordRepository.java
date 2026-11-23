package com.billim.global.repository;

import com.billim.global.entity.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    // 회원 잠금 대기 전에 생성된 MySQL 스냅샷 대신 최신 커밋 조회.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdempotencyRecord> findByMemberIdAndOperationKeyAndRequestKey(
            Long memberId, String operationKey, String requestKey);
}
