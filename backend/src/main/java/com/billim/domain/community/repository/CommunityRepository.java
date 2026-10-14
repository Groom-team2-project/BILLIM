package com.billim.domain.community.repository;

import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    /** 동네 이름 검색. 활성 동네만 이름순·id순 (API 명세 E_009) */
    Page<Community> findByStatusAndNameContainingOrderByNameAscIdAsc(
            CommunityStatus status, String keyword, Pageable pageable);

    Page<Community> findByStatusOrderByNameAscIdAsc(CommunityStatus status, Pageable pageable);

    /** 비활성 동네는 404로 숨긴다 (API 명세 E_010) */
    Optional<Community> findByIdAndStatus(Long id, CommunityStatus status);

    /**
     * 주변 동네 계산 대상. 활성 동네 전체를 읽어 거리를 서버에서 계산한다 (API 명세 E_071).
     * 초기 운영 규모(커뮤니티 1~2개)에서는 전체 조회가 더 단순하고 정확하다.
     * 커뮤니티 수가 늘어 병목이 확인되면 좌표 범위 선필터를 1차 회고 근거와 함께 넣는다.
     */
    List<Community> findByStatus(CommunityStatus status);
}
