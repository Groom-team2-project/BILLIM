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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * communities — 생활권 커뮤니티 기준 정보.
 * 좌표는 공개 커뮤니티 중심이며 개인 위치가 아니다. 기준 데이터는 운영 seed로 등록한다. (ERD 4.2)
 */
@Entity
@Table(
        name = "communities",
        indexes = @Index(name = "idx_communities_status_name", columnList = "status,name,id")
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

    @Column(name = "center_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal centerLatitude;

    @Column(name = "center_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal centerLongitude;

    @Column(name = "verification_radius_m", nullable = false)
    private int verificationRadiusM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CommunityStatus status = CommunityStatus.ACTIVE;

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

    public boolean isActive() {
        return status == CommunityStatus.ACTIVE;
    }

    public double centerLatitudeValue() {
        return centerLatitude.doubleValue();
    }

    public double centerLongitudeValue() {
        return centerLongitude.doubleValue();
    }
}
