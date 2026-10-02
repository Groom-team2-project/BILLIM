package com.billim.domain.item.entity;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final LocalDate START = LocalDate.of(2026, 10, 5);
    private static final LocalDate END = LocalDate.of(2026, 10, 10);

    private Item newItem() {
        return Item.create(1L, 100L, 1L, 10L, "전동드릴", "설명", START, END, NOW);
    }

    private static void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    // ───── 날짜 유효성 ─────

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 거부한다")
    void rejectsStartAfterEnd() {
        assertCode(() -> Item.create(1L, 100L, 1L, 10L, "제목", null, END, START, NOW), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("시작일과 종료일이 같아도 허용한다")
    void allowsSameStartAndEnd() {
        Item item = Item.create(1L, 100L, 1L, 10L, "제목", null, START, START, NOW);
        assertThat(item.getAvailableStartDate()).isEqualTo(item.getAvailableEndDate());
    }

    @Test
    @DisplayName("날짜가 없으면 거부한다")
    void rejectsMissingDate() {
        assertCode(() -> Item.create(1L, 100L, 1L, 10L, "제목", null, null, END, NOW), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("제목이 비었거나 100자를 넘으면 거부한다")
    void rejectsBlankOrTooLongTitle() {
        assertCode(() -> Item.create(1L, 100L, 1L, 10L, " ", null, START, END, NOW), ErrorCode.INVALID_REQUEST);
        assertCode(() -> Item.create(1L, 100L, 1L, 10L, "가".repeat(101), null, START, END, NOW), ErrorCode.INVALID_REQUEST);
        assertThat(Item.create(1L, 100L, 1L, 10L, "가".repeat(100), null, START, END, NOW).getTitle()).hasSize(100);
    }

    @Test
    @DisplayName("설명이 3000자를 넘으면 거부하고 빈 설명은 null로 저장한다")
    void rejectsTooLongDescriptionAndStoresBlankAsNull() {
        assertCode(() -> Item.create(1L, 100L, 1L, 10L, "제목", "a".repeat(3001), START, END, NOW), ErrorCode.INVALID_REQUEST);
        assertThat(Item.create(1L, 100L, 1L, 10L, "제목", "  ", START, END, NOW).getDescription()).isNull();
    }

    @Test
    @DisplayName("등록하면 초기 상태는 PUBLIC이다")
    void createdItemIsPublic() {
        Item item = newItem();
        assertThat(item.getVisibility()).isEqualTo(ItemVisibility.PUBLIC);
        assertThat(item.getDeletedAt()).isNull();
        assertThat(item.isOwnedBy(1L)).isTrue();
        assertThat(item.isOwnedBy(2L)).isFalse();
    }

    // ───── 공개·숨김 ─────

    @Test
    @DisplayName("공개와 숨김을 전환한다")
    void togglesPublicAndHidden() {
        Item item = newItem();
        Instant later = NOW.plusSeconds(60);
        item.changeVisibility(ItemVisibility.HIDDEN, later);
        assertThat(item.getVisibility()).isEqualTo(ItemVisibility.HIDDEN);
        assertThat(item.getUpdatedAt()).isEqualTo(later);
        item.changeVisibility(ItemVisibility.PUBLIC, later);
        assertThat(item.getVisibility()).isEqualTo(ItemVisibility.PUBLIC);
    }

    @Test
    @DisplayName("변경없는 공개 상태 요청은 updatedAt을 바꾸지 않는다")
    void sameVisibilityKeepsUpdatedAt() {
        Item item = newItem();
        item.changeVisibility(ItemVisibility.PUBLIC, NOW.plusSeconds(60));
        assertThat(item.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("공개 상태로 DELETED를 지정할 수 없다")
    void rejectsDeletedAsVisibility() {
        assertCode(() -> newItem().changeVisibility(ItemVisibility.DELETED, NOW), ErrorCode.INVALID_REQUEST);
    }

    // ───── 소프트 삭제 ─────

    @Test
    @DisplayName("삭제하면 visibility가 DELETED이고 deletedAt이 기록된다")
    void softDeleteSetsDeletedAndTimestamp() {
        Item item = newItem();
        Instant at = NOW.plusSeconds(10);
        item.delete(at);
        assertThat(item.getVisibility()).isEqualTo(ItemVisibility.DELETED);
        assertThat(item.getDeletedAt()).isEqualTo(at);
        assertThat(item.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제된 물건은 상태 변경 수정 재삭제 사진교체가 모두 거부된다")
    void deletedItemRejectsAllChanges() {
        Item item = newItem();
        item.delete(NOW);
        assertCode(() -> item.changeVisibility(ItemVisibility.PUBLIC, NOW), ErrorCode.INVALID_STATE_TRANSITION);
        assertCode(() -> item.update(1L, 10L, "새 제목", null, START, END, NOW), ErrorCode.INVALID_STATE_TRANSITION);
        assertCode(() -> item.delete(NOW), ErrorCode.INVALID_STATE_TRANSITION);
        assertCode(() -> item.replaceImages(List.of(1L), NOW), ErrorCode.INVALID_STATE_TRANSITION);
    }

    // ───── 이미지 연결과 순서 ─────

    @Test
    @DisplayName("이미지는 전달한 순서대로 sortOrder가 매겨지고 첫번째가 대표다")
    void imagesFollowGivenOrderAndFirstIsRepresentative() {
        Item item = newItem();
        item.replaceImages(List.of(5L, 3L, 9L), NOW);
        assertThat(item.getImages()).extracting(ItemImage::getMediaFileId).containsExactly(5L, 3L, 9L);
        assertThat(item.getImages()).extracting(ItemImage::getSortOrder).containsExactly(0, 1, 2);
        assertThat(item.getImages().get(0).isRepresentative()).isTrue();
        assertThat(item.getImages().get(1).isRepresentative()).isFalse();
    }

    @Test
    @DisplayName("순서를 바꾸면 맨 앞으로 옮긴 이미지의 sortOrder가 0이 된다")
    void reorderMovesImageToSortOrderZero() {
        Item item = newItem();
        item.replaceImages(List.of(1L, 2L, 5L), NOW);
        item.clearImages();
        item.replaceImages(List.of(5L, 1L, 2L), NOW);
        ItemImage first = item.getImages().get(0);
        assertThat(first.getMediaFileId()).isEqualTo(5L);
        assertThat(first.getSortOrder()).isZero();
    }

    @Test
    @DisplayName("이미지는 1개 이상 5개 이하여야 한다")
    void requiresOneToFiveImages() {
        Item item = newItem();
        assertCode(() -> item.replaceImages(List.of(), NOW), ErrorCode.INVALID_REQUEST);
        assertCode(() -> item.replaceImages(List.of(1L, 2L, 3L, 4L, 5L, 6L), NOW), ErrorCode.INVALID_REQUEST);
        item.replaceImages(List.of(1L, 2L, 3L, 4L, 5L), NOW);
        assertThat(item.getImages()).hasSize(5);
    }

    @Test
    @DisplayName("이미지 ID가 중복되거나 null이면 거부한다")
    void rejectsDuplicateOrNullImageIds() {
        assertCode(() -> newItem().replaceImages(List.of(1L, 1L), NOW), ErrorCode.INVALID_REQUEST);
        assertCode(() -> newItem().replaceImages(Arrays.asList(1L, null), NOW), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("이미지가 이미 있는데 비우지 않고 교체하면 거부한다")
    void rejectsReplaceWithoutClear() {
        Item item = newItem();
        item.replaceImages(List.of(1L), NOW);
        assertThatThrownBy(() -> item.replaceImages(List.of(2L), NOW)).isInstanceOf(IllegalStateException.class);
    }

    // ───── 수정·버전 ─────

    @Test
    @DisplayName("수정하면 필드와 updatedAt이 바뀌고 소유자와 커뮤니티는 유지된다")
    void updateChangesFieldsButKeepsOwnerAndCommunity() {
        Item item = newItem();
        Instant later = NOW.plusSeconds(5);
        item.update(2L, 11L, "새 제목", "새 설명", START.plusDays(1), END.plusDays(1), later);
        assertThat(item.getTitle()).isEqualTo("새 제목");
        assertThat(item.getCategoryId()).isEqualTo(2L);
        assertThat(item.getPlaceId()).isEqualTo(11L);
        assertThat(item.getAvailableStartDate()).isEqualTo(START.plusDays(1));
        assertThat(item.getUpdatedAt()).isEqualTo(later);
        assertThat(item.getOwnerId()).isEqualTo(1L);
        assertThat(item.getCommunityId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("설명을 생략하면 설명이 제거된다")
    void omittedDescriptionIsRemoved() {
        Item item = newItem();
        item.update(1L, 10L, "제목", null, START, END, NOW);
        assertThat(item.getDescription()).isNull();
    }

    @Test
    @DisplayName("영속화 전에는 version이 비어있다")
    void versionIsNullBeforePersist() {
        // version 증가는 JPA @Version이 flush 시점에 처리한다. 충돌 판정은 ItemServiceTest, 실제 증가는 Repository 테스트에서 확인
        assertThat(newItem().getVersion()).isNull();
    }
}
