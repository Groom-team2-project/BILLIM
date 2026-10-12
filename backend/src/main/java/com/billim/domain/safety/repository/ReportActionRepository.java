package com.billim.domain.safety.repository;

import com.billim.domain.safety.entity.ReportAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReportActionRepository extends JpaRepository<ReportAction, Long> {

    Optional<ReportAction> findByReportId(long reportId);

    /** 목록 응답에서 신고별 처리 결과를 한 번에 붙인다. 건당 조회를 피한다 */
    List<ReportAction> findByReportIdIn(Collection<Long> reportIds);
}
