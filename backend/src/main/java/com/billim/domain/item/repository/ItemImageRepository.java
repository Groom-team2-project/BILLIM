package com.billim.domain.item.repository;

import com.billim.domain.item.entity.ItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

    interface Thumbnail {
        Long getItemId();

        Long getMediaFileId();
    }

    /** 목록 썸네일(대표 사진, sort_order 0)을 한 번에 조회 — 항목마다 조회하지 않는다. */
    @Query("select ii.item.id as itemId, ii.mediaFileId as mediaFileId from ItemImage ii "
            + "where ii.item.id in :itemIds and ii.sortOrder = 0")
    List<Thumbnail> findThumbnails(@Param("itemIds") Collection<Long> itemIds);

    /** 사진이 연결된 물건 ID (사진 읽기 권한 판정) */
    @Query("select ii.item.id from ItemImage ii where ii.mediaFileId = :mediaFileId")
    Optional<Long> findItemIdByMediaFileId(@Param("mediaFileId") Long mediaFileId);
}
