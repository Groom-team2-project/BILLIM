package com.billim.domain.community.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * communities — 생활권 커뮤니티 기준 정보.
 * 좌표는 공개 커뮤니티 중심이며 개인 위치가 아니다.
 * 행정동 단위 동네는 주변 동네 조회에서 자동 등록되고, 경계가 행정동과 다른 동네는 운영 seed로
 * 등록하며 행정동 코드를 비운다. (ERD 4.2, 도메인 가이드 결정 '행정동 위치 인증')
 */
@Entity
@Table(
        name = "communities",
        indexes = @Index(name = "idx_communities_status_name", columnList = "status,name,id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_communities_region_code", columnNames = "region_code")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Community extends BaseTimeEntity {

    public static final int NAME_MAX = 100;
    public static final int DISPLAY_ADDRESS_MAX = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    /** 공개 커뮤니티 주소. 개인 집 주소가 아니다 */
    @Column(name = "display_address", nullable = false, length = DISPLAY_ADDRESS_MAX)
    private String displayAddress;

    /**
     * 행정동 코드 10자리. 역지오코딩 결과와 비교해 동네 동일성을 판정한다.
     *
     * <p>NULL이면 행정동으로 판정할 수 없는 동네이며 반경 판정만 쓴다. 반경만으로 운영하던 기존 행과
     * 아파트 단지처럼 행정동과 경계가 다른 동네가 여기 해당한다.
     */
    @Column(name = "region_code", length = Region.CODE_LENGTH)
    private String regionCode;

    @Column(name = "center_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal centerLatitude;

    @Column(name = "center_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal centerLongitude;

    @Column(name = "verification_radius_m", nullable = false)
    private int verificationRadiusM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CommunityStatus status = CommunityStatus.ACTIVE;

    /**
     * 공용 장소 자동 보충을 마지막으로 시도한 시각.
     *
     * <p>채울 수 없는 동네를 매 조회마다 다시 시도하지 않기 위한 값이다. 인메모리로 두면
     * 인스턴스마다 따로 세어 여러 대로 늘렸을 때 외부 호출이 그만큼 늘어난다.
     * 성공하면 장소가 생겨 보충이 다시 돌지 않으므로, 사실상 실패한 시도를 기억한다.
     * (도메인 가이드 결정 '공용 장소 자동 보충')
     */
    @Column(name = "place_supply_attempted_at")
    private Instant placeSupplyAttemptedAt;

    /** 운영 seed·테스트 픽스처용 생성 */
    public static Community of(String name, String displayAddress,
                               BigDecimal centerLatitude, BigDecimal centerLongitude,
                               int verificationRadiusM, CommunityStatus status) {
        if (verificationRadiusM <= 0) {
            throw new IllegalArgumentException("인증 반경은 양수여야 합니다.");
        }
        Community community = new Community();
        community.name = name;
        community.displayAddress = displayAddress;
        community.centerLatitude = centerLatitude;
        community.centerLongitude = centerLongitude;
        community.verificationRadiusM = verificationRadiusM;
        community.status = status;
        return community;
    }

    /** 역지오코딩으로 찾은 행정동을 그대로 동네로 등록한다. 좌표·주소는 지도 제공자의 행정동 대표 값이다 */
    public static Community ofRegion(Region region, int verificationRadiusM) {
        Community community = of(
                region.name(), region.displayAddress(),
                BigDecimal.valueOf(region.latitude()), BigDecimal.valueOf(region.longitude()),
                verificationRadiusM, CommunityStatus.ACTIVE);
        community.regionCode = region.code();
        return community;
    }

    /** 운영 seed·테스트 픽스처에서 기존 동네에 행정동 코드를 붙일 때 쓴다 */
    public Community assignRegionCode(String regionCode) {
        this.regionCode = regionCode;
        return this;
    }

    /** 행정동 코드가 같은지. 코드가 없는 동네는 항상 false이며 반경 판정으로 넘어간다 */
    public boolean matchesRegion(String code) {
        return regionCode != null && regionCode.equals(code);
    }

    public boolean isActive() {
        return status == CommunityStatus.ACTIVE;
    }

    /** 장소 보충을 다시 시도할 때가 됐는지 */
    public boolean canAttemptPlaceSupply(Instant now, Duration retryAfter) {
        return placeSupplyAttemptedAt == null
                || !placeSupplyAttemptedAt.plus(retryAfter).isAfter(now);
    }

    public void markPlaceSupplyAttempted(Instant now) {
        this.placeSupplyAttemptedAt = now;
    }

    public double centerLatitudeValue() {
        return centerLatitude.doubleValue();
    }

    public double centerLongitudeValue() {
        return centerLongitude.doubleValue();
    }
}
