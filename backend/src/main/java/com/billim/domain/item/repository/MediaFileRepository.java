package com.billim.domain.item.repository;

import com.billim.domain.item.entity.MediaFile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    /** 물건 연결 시 같은 사진의 동시 연결을 막기 위해 행 잠금 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaFile m where m.id in :ids")
    List<MediaFile> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaFile m where m.id = :id")
    Optional<MediaFile> findByIdForUpdate(@Param("id") Long id);
}
