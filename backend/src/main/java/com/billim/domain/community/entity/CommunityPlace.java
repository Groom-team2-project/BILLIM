package com.billim.domain.community.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * community_places — 물건과 약속이 공유하는 공용 장소.
 * 거래 장소는 이 목록에서만 선택한다. 집 주소는 입력·수집하지 않는다. (정책 및 상태 5 거래 장소)
 * 사용된 장소는 물리 삭제하지 않고 active를 내린다.
 */
@Entity
@Table(
        name = "community_places",
        indexes = @Index(name = "idx_community_places_community_active",
                columnList = "community_id,active,id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPlace extends BaseTimeEntity {

    public static final int NAME_MAX = 100;
    public static final int GUIDE_MAX = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false, updatable = false)
    private Long communityId;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = GUIDE_MAX)
    private String guide;

    @Column(nullable = false)
    private boolean active = true;

    /** 운영 seed·테스트 픽스처용 생성 */
    public static CommunityPlace of(long communityId, String name,
                                    BigDecimal latitude, BigDecimal longitude,
                                    String guide, boolean active) {
        CommunityPlace place = new CommunityPlace();
        place.communityId = communityId;
        place.name = name;
        place.latitude = latitude;
        place.longitude = longitude;
        place.guide = guide == null || guide.isBlank() ? null : guide.strip();
        place.active = active;
        return place;
    }

    public boolean belongsTo(long communityId) {
        return this.communityId == communityId;
    }
}
