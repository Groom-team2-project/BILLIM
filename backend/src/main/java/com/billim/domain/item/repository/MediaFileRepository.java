package com.billim.domain.item.repository;

import com.billim.domain.item.entity.MediaFile;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    // 같은 사진의 동시 연결 방지를 위한 행 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaFile m where m.id in :ids")
    List<MediaFile> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaFile m where m.id = :id")
    Optional<MediaFile> findByIdForUpdate(@Param("id") Long id);

    // ───── 정리 작업 (MediaCleanupJob). 모두 id 커서로 묶음 단위 진행 ─────

    interface StoredMedia {
        Long getId();

        String getStorageKey();
    }

    // 만료된 임시 사진 (물건에 연결되지 않은 채 24시간 경과)
    @Query("select m.id as id, m.storageKey as storageKey from MediaFile m "
            + "where m.status = com.billim.domain.item.entity.MediaStatus.TEMP "
            + "and m.expiresAt < :threshold and m.id > :afterId order by m.id")
    List<StoredMedia> findExpiredTemp(@Param("threshold") Instant threshold, @Param("afterId") long afterId,
                                      Pageable page);

    // 수정에서 빠져 삭제 표시된 뒤 보관 기간이 지난 사진. 물건에 연결된 것은 제외
    @Query("select m.id as id, m.storageKey as storageKey from MediaFile m "
            + "where m.status = com.billim.domain.item.entity.MediaStatus.DELETED "
            + "and m.deletedAt < :threshold and m.id > :afterId "
            + "and not exists (select ii.id from ItemImage ii where ii.mediaFileId = m.id) order by m.id")
    List<StoredMedia> findDeletedBefore(@Param("threshold") Instant threshold, @Param("afterId") long afterId,
                                        Pageable page);

    // 삭제된 지 보관 기간이 지난 물건의 사진
    @Query("select m.id as id, m.storageKey as storageKey from MediaFile m, ItemImage ii join ii.item i "
            + "where ii.mediaFileId = m.id "
            + "and i.visibility = com.billim.domain.item.entity.ItemVisibility.DELETED "
            + "and i.deletedAt < :threshold and m.id > :afterId order by m.id")
    List<StoredMedia> findOfItemsDeletedBefore(@Param("threshold") Instant threshold, @Param("afterId") long afterId,
                                               Pageable page);

    @Modifying(clearAutomatically = true)
    @Query("delete from MediaFile m where m.id in :ids")
    int deleteAllByIdIn(@Param("ids") Collection<Long> ids);
}
