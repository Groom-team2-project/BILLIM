package com.billim.domain.community.repository;

import com.billim.domain.community.entity.CommunityPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityPlaceRepository extends JpaRepository<CommunityPlace, Long> {

    /** 현재 active 장소만 이름순·id순 (API 명세 E_012) */
    List<CommunityPlace> findByCommunityIdAndActiveTrueOrderByNameAscIdAsc(long communityId);

    /** 물건·약속의 장소 검증용. 비활성 장소도 찾아 호출 측이 판단한다 */
    Optional<CommunityPlace> findByIdAndCommunityId(long id, long communityId);
}
