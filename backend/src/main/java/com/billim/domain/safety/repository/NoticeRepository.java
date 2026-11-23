package com.billim.domain.safety.repository;

import com.billim.domain.safety.entity.Notice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 동네 공지 목록 (API 명세 E_063). 삭제 제외, 고정 먼저 최신순 */
    Page<Notice> findByCommunityIdAndDeletedAtIsNullOrderByPinnedDescCreatedAtDescIdDesc(
            long communityId, Pageable pageable);

    /** 관리자 목록 (API 명세 E_065). 동네 필터는 서비스에서 분기한다 */
    Page<Notice> findByDeletedAtIsNullOrderByPinnedDescCreatedAtDescIdDesc(Pageable pageable);

    Optional<Notice> findByIdAndDeletedAtIsNull(long id);

    /** 수정·삭제에서 공지 행을 잠근다. version 재검사와 함께 쓴다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notice n WHERE n.id = :id")
    Optional<Notice> findByIdForUpdate(@Param("id") long id);
}
