package com.billim.domain.item.entity;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 대여 물건과 대여 가능 기간. owner·community는 생성 후 변경하지 않는다. */
@Entity
@Table(name = "items")
public class Item extends TimestampedEntity {

    public static final int MAX_IMAGES = 5;
    public static final int MAX_TITLE = 100;
    public static final int MAX_DESCRIPTION = 3000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "community_id", nullable = false, updatable = false)
    private Long communityId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(nullable = false, length = MAX_TITLE)
    private String title;

    @Column(length = MAX_DESCRIPTION)
    private String description;

    @Column(name = "available_start_date", nullable = false)
    private LocalDate availableStartDate;

    @Column(name = "available_end_date", nullable = false)
    private LocalDate availableEndDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // ERD는 VARCHAR + CHECK. 네이티브 ENUM 매핑 방지
    @Column(nullable = false, length = 10)
    private ItemVisibility visibility;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ItemImage> images = new ArrayList<>();

    protected Item() {
    }

    /** 초기 상태는 PUBLIC. 사진은 {@link #replaceImages}로 연결한다. */
    public static Item create(long ownerId, long communityId, long categoryId, long placeId,
                              String title, String description,
                              LocalDate availableStartDate, LocalDate availableEndDate, Instant now) {
        Item item = new Item();
        item.ownerId = ownerId;
        item.communityId = communityId;
        item.visibility = ItemVisibility.PUBLIC;
        item.applyFields(categoryId, placeId, title, description, availableStartDate, availableEndDate);
        item.initTimestamps(now);
        return item;
    }

    /** 소유자·커뮤니티는 바꾸지 않는다. 삭제된 물건은 수정할 수 없다. */
    public void update(long categoryId, long placeId, String title, String description,
                       LocalDate availableStartDate, LocalDate availableEndDate, Instant now) {
        requireNotDeleted();
        applyFields(categoryId, placeId, title, description, availableStartDate, availableEndDate);
        touch(now);
    }

    /** PUBLIC ↔ HIDDEN. 삭제된 물건은 변경할 수 없다. */
    public void changeVisibility(ItemVisibility target, Instant now) {
        requireNotDeleted();
        if (target != ItemVisibility.PUBLIC && target != ItemVisibility.HIDDEN) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (visibility == target) {
            return;
        }
        this.visibility = target;
        touch(now);
    }

    /** 논리 삭제. 행과 사진은 물리 삭제하지 않는다. */
    public void delete(Instant now) {
        requireNotDeleted();
        this.visibility = ItemVisibility.DELETED;
        this.deletedAt = now;
        touch(now);
    }

    /**
     * 사진 전체를 주어진 순서로 다시 구성한다. 첫 번째가 대표 사진(sort_order 0).
     * unique (item_id, sort_order) 때문에 이미 저장된 사진을 바꿀 때는
     * 호출 전에 {@link #clearImages()} 후 flush가 필요하다.
     */
    public void replaceImages(List<Long> orderedMediaIds, Instant now) {
        requireNotDeleted();
        validateImageIds(orderedMediaIds);
        if (!images.isEmpty()) {
            throw new IllegalStateException("기존 사진을 먼저 clearImages로 비우고 flush해야 한다.");
        }
        for (int i = 0; i < orderedMediaIds.size(); i++) {
            images.add(new ItemImage(this, orderedMediaIds.get(i), i, now));
        }
        touch(now);
    }

    public void clearImages() {
        images.clear();
    }

    /** 1~5개, null·중복 금지 */
    public static void validateImageIds(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty() || mediaIds.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "사진은 1장 이상 5장 이하로 등록해 주세요.");
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : mediaIds) {
            if (id == null || !seen.add(id)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "같은 사진을 중복해서 등록할 수 없습니다.");
            }
        }
    }

    public boolean isOwnedBy(long memberId) {
        return ownerId.equals(memberId);
    }

    public boolean isDeleted() {
        return visibility == ItemVisibility.DELETED;
    }

    private void requireNotDeleted() {
        if (isDeleted()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    private void applyFields(long categoryId, long placeId, String title, String description,
                             LocalDate start, LocalDate end) {
        if (title == null || title.isBlank() || title.length() > MAX_TITLE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "제목은 1자 이상 100자 이하로 입력해 주세요.");
        }
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "설명은 3000자 이하로 입력해 주세요.");
        }
        if (start == null || end == null || start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "대여 가능 기간이 올바르지 않습니다.");
        }
        this.categoryId = categoryId;
        this.placeId = placeId;
        this.title = title;
        // 빈 문자열은 NULL 대용 금지 (ERD 1장)
        this.description = (description == null || description.isBlank()) ? null : description;
        this.availableStartDate = start;
        this.availableEndDate = end;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getCommunityId() {
        return communityId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getAvailableStartDate() {
        return availableStartDate;
    }

    public LocalDate getAvailableEndDate() {
        return availableEndDate;
    }

    public ItemVisibility getVisibility() {
        return visibility;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<ItemImage> getImages() {
        return Collections.unmodifiableList(images);
    }
}
