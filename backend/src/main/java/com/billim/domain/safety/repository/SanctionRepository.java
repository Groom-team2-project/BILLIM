package com.billim.domain.safety.repository;

import com.billim.domain.safety.entity.Sanction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {

    /** 제재 이력 (API 명세 13.4). 만료·해제까지 모두 보여준다 */
    Page<Sanction> findAllByOrderByStartsAtDescIdDesc(Pageable pageable);

    Page<Sanction> findByMemberIdOrderByStartsAtDescIdDesc(long memberId, Pageable pageable);

    /** 내 운영 조치 목록에서 조치별 제재 기간을 한 번에 붙인다. 경고에는 제재 행이 없다 */
    List<Sanction> findByReportActionIdIn(Collection<Long> reportActionIds);

    /**
     * 지금 회원을 제한하는 제재가 하나라도 있는지. 유효 판정은 ERD 4.7 정의를 그대로 옮겼다.
     * 요청 차단 적용은 인증·회원 도메인이 이 결과를 보고 수행한다.
     */
    @Query("""
            SELECT COUNT(s) > 0
              FROM Sanction s
             WHERE s.memberId = :memberId
               AND s.revokedAt IS NULL
               AND s.startsAt <= :now
               AND (s.endsAt IS NULL OR s.endsAt > :now)
            """)
    boolean existsEffective(@Param("memberId") long memberId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sanction s WHERE s.id = :id")
    Optional<Sanction> findByIdForUpdate(@Param("id") long id);
}
