package com.billim.domain.safety.repository;

import com.billim.domain.safety.entity.Report;
import com.billim.domain.safety.entity.ReportReasonCode;
import com.billim.domain.safety.entity.ReportStatus;
import com.billim.domain.safety.entity.ReportTargetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 내 접수 내역 (API 명세 12.5). 최신순 */
    Page<Report> findByReporterIdOrderByCreatedAtDescIdDesc(long reporterId, Pageable pageable);

    /** 운영 신고 목록 (API 명세 13.1). status·communityId는 선택 필터라 서비스에서 분기한다 */
    Page<Report> findByStatusOrderByCreatedAtDescIdDesc(ReportStatus status, Pageable pageable);

    Page<Report> findByCommunityIdOrderByCreatedAtDescIdDesc(long communityId, Pageable pageable);

    Page<Report> findByCommunityIdAndStatusOrderByCreatedAtDescIdDesc(
            long communityId, ReportStatus status, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /**
     * 같은 대상·사유의 미처리 신고가 이미 있는지. 중복 접수 거부 기준 (ERD 4.7).
     * MEMBER는 target_member_id, ITEM·MESSAGE는 각자의 참조 컬럼으로 비교하므로
     * 대상 종류와 함께 해석한 대상 회원까지 같이 본다.
     */
    @Query("""
            SELECT COUNT(r) > 0
              FROM Report r
             WHERE r.reporterId = :reporterId
               AND r.targetType = :targetType
               AND r.targetMemberId = :targetMemberId
               AND r.reasonCode = :reasonCode
               AND r.status = com.billim.domain.safety.entity.ReportStatus.PENDING
               AND (:targetRef IS NULL
                    OR r.targetItemId = :targetRef
                    OR r.targetMessageId = :targetRef)
            """)
    boolean existsPendingDuplicate(@Param("reporterId") long reporterId,
                                   @Param("targetType") ReportTargetType targetType,
                                   @Param("targetMemberId") long targetMemberId,
                                   @Param("reasonCode") ReportReasonCode reasonCode,
                                   @Param("targetRef") Long targetRef);

    /** 처리 트랜잭션에서 신고 행을 잠근다 (API 명세 13.3) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") long id);
}
