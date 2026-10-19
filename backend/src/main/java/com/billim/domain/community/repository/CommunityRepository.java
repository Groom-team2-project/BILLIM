package com.billim.domain.community.repository;

import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    /** 동네 이름 검색. 활성 동네만 이름순·id순 (API 명세 E_009) */
    Page<Community> findByStatusAndNameContainingOrderByNameAscIdAsc(
            CommunityStatus status, String keyword, Pageable pageable);

    Page<Community> findByStatusOrderByNameAscIdAsc(CommunityStatus status, Pageable pageable);

    /** 비활성 동네는 404로 숨긴다 (API 명세 E_010) */
    Optional<Community> findByIdAndStatus(Long id, CommunityStatus status);

    /** 행정동 코드로 동네를 찾는다. 역지오코딩 결과를 동네로 환원할 때 쓴다 */
    Optional<Community> findByRegionCodeAndStatus(String regionCode, CommunityStatus status);

    /**
     * 주변 동네 계산 대상 (API 명세 E_071). 좌표 사각형으로 먼저 줄이고 거리는 서버에서 계산한다.
     *
     * <p>행정동 자동 등록으로 동네 행이 전국 규모(수천 개)까지 늘어나므로 전체 조회는 쓰지 않는다.
     * 사각형은 후보를 줄이는 용도라 넉넉하고, 정확한 거리·정렬은 하버사인이 다시 한다.
     */
    @Query("""
            select c from Community c
            where c.status = :status
              and c.centerLatitude between :minLatitude and :maxLatitude
              and c.centerLongitude between :minLongitude and :maxLongitude
            """)
    List<Community> findWithinBox(@Param("status") CommunityStatus status,
                                  @Param("minLatitude") BigDecimal minLatitude,
                                  @Param("maxLatitude") BigDecimal maxLatitude,
                                  @Param("minLongitude") BigDecimal minLongitude,
                                  @Param("maxLongitude") BigDecimal maxLongitude);
}
