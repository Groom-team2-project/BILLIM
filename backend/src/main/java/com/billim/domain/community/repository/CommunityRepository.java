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

    /**
     * 동네 검색. 활성 동네만 이름순·id순 (API 명세 E_009).
     *
     * <p>이름과 공개 주소를 함께 본다. 행정동 동네의 이름은 "역삼1동"이라 "강남구"처럼
     * 상위 지역으로 찾으려면 주소도 봐야 한다.
     *
     * <p>세 조건은 모두 선택이고 빈 문자열이면 걸지 않는다. 지역 범위는 둘로 갈린다.
     * 읍·면·동까지 고르면 공개 주소가 그 값으로 확정되므로 같은지 비교하고(exactAddress),
     * 그 위 단계까지만 고르면 접두사로 좁힌다(prefix). 접두사는 토큰 끝에 공백을 두어
     * "강남구"가 "강남구갑" 같은 이름에 걸리지 않게 한다.
     *
     * <p>{@code membersOnly}는 가입자가 있는 동네만 남긴다. 검색만 해도 행정동이 동네로
     * 자동 등록되므로(결정 '행정동 위치 인증') 아무도 없는 동네가 섞인다. 관리자 화면은
     * 통계·공지 대상이 있는 동네만 골라야 한다.
     *
     * <p>기준은 ACTIVE 소속의 존재이고 인증 만료는 보지 않는다. {@code verifiedMemberCount}
     * (유효 인증 회원 수)와 다르다 — 전원 만료된 동네도 가입자는 있고 재인증하면 돌아오므로,
     * 그 동네를 목록에서 지우면 공지를 쓸 수 없게 된다.
     *
     * @param prefix        상위 단계까지 이어 붙인 공개 주소 접두사. 끝에 공백을 포함한다
     * @param exactAddress  읍·면·동까지 확정된 공개 주소
     * @param keyword       이름·공개 주소 부분 검색
     * @param membersOnly   true면 ACTIVE 소속이 하나라도 있는 동네만
     */
    @Query("""
            select c from Community c
            where c.status = :status
              and (:prefix = '' or c.displayAddress like concat(:prefix, '%') escape '!')
              and (:exactAddress = '' or c.displayAddress = :exactAddress)
              and (:keyword = '' or c.name like concat('%', :keyword, '%')
                   or c.displayAddress like concat('%', :keyword, '%'))
              and (:membersOnly = false or exists (
                   select 1 from CommunityMembership m
                    where m.communityId = c.id
                      and m.status = com.billim.domain.community.entity.MembershipStatus.ACTIVE))
            order by c.name asc, c.id asc
            """)
    Page<Community> search(@Param("status") CommunityStatus status,
                           @Param("prefix") String prefix,
                           @Param("exactAddress") String exactAddress,
                           @Param("keyword") String keyword,
                           @Param("membersOnly") boolean membersOnly,
                           Pageable pageable);

    /**
     * 동네 선택 단계 옵션을 집계할 공개 주소 (API 명세 E_073).
     *
     * <p>상위 단계 접두사로 먼저 줄이고 단계 쪼개기는 서버가 한다
     * ({@code RegionPath}). 전국 행정동이 등록되면 수천 행이라 접두사가 중요하다.
     *
     * <p>{@code membersOnly}의 뜻은 {@link #search}와 같다.
     */
    @Query("""
            select c.displayAddress from Community c
            where c.status = :status
              and (:prefix = '' or c.displayAddress like concat(:prefix, '%') escape '!')
              and (:membersOnly = false or exists (
                   select 1 from CommunityMembership m
                    where m.communityId = c.id
                      and m.status = com.billim.domain.community.entity.MembershipStatus.ACTIVE))
            """)
    List<String> findDisplayAddresses(@Param("status") CommunityStatus status,
                                      @Param("prefix") String prefix,
                                      @Param("membersOnly") boolean membersOnly);

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
