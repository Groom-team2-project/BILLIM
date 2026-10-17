package com.billim.domain.item.repository;

import com.billim.domain.item.entity.Item;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {

    @Query("select i.ownerId from Item i where i.id = :id")
    Optional<Long> findOwnerId(@Param("id") Long id);

    // 동네의 공개 물건 소유자 (검색 차단 판정 후보)
    @Query("select distinct i.ownerId from Item i where i.communityId = :communityId "
            + "and i.visibility = com.billim.domain.item.entity.ItemVisibility.PUBLIC")
    List<Long> findPublicOwnerIds(@Param("communityId") long communityId);

    /** 수정·삭제·공개 변경은 물건 행 잠금 아래에서 (API 명세 B_021) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") Long id);
}
