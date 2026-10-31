package com.billim.domain.safety.repository;

import com.billim.domain.safety.entity.ReportAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReportActionRepository extends JpaRepository<ReportAction, Long> {

    Optional<ReportAction> findByReportId(long reportId);

    /** 목록 응답에서 신고별 처리 결과를 한 번에 붙인다. 건당 조회를 피한다 */
    List<ReportAction> findByReportIdIn(Collection<Long> reportIds);

    /**
     * 회원이 받은 운영 조치 (API 명세 12.7 E_072).
     *
     * <p>{@code sanctions}가 아니라 이 테이블을 읽는다. 경고(WARN)는 제재 행을 만들지 않아
     * 제재 테이블만 보면 보이지 않는다. (API 명세 13.3)
     *
     * <p>반려(DISMISS)는 제외한다. 대상에게 알리지도 않는 조치라 목록에 넣으면
     * 신고당한 사실만 알려 주게 된다.
     *
     * <p>신고 엔티티와 연관을 두지 않아 id로 조인한다. 조회 조건은 신고의 대상 회원이고,
     * 신고자·사유 코드·대상 거래는 응답에 담지 않는다.
     */
    @Query(value = """
            SELECT a
              FROM ReportAction a, Report r
             WHERE r.id = a.reportId
               AND r.targetMemberId = :memberId
               AND a.action <> com.billim.domain.safety.entity.ReportActionType.DISMISS
             ORDER BY a.createdAt DESC, a.id DESC
            """,
            countQuery = """
                    SELECT COUNT(a)
                      FROM ReportAction a, Report r
                     WHERE r.id = a.reportId
                       AND r.targetMemberId = :memberId
                       AND a.action <> com.billim.domain.safety.entity.ReportActionType.DISMISS
                    """)
    Page<ReportAction> findReceivedByMemberId(@Param("memberId") long memberId, Pageable pageable);
}
