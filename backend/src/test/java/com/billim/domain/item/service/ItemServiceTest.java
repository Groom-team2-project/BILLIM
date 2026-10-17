package com.billim.domain.item.service;

import com.billim.domain.community.entity.CommunityPlace;
import com.billim.domain.community.repository.CommunityPlaceRepository;
import com.billim.domain.item.dto.CreateItemRequest;
import com.billim.domain.item.dto.ItemDetailResponse;
import com.billim.domain.item.dto.ItemPageResponse;
import com.billim.domain.item.dto.ItemSearchCondition;
import com.billim.domain.item.dto.ItemSort;
import com.billim.domain.item.dto.UpdateItemRequest;
import com.billim.domain.item.dto.VisibilityChangeRequest;
import com.billim.domain.item.entity.Category;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemImage;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.entity.MediaFile;
import com.billim.domain.item.entity.MediaStatus;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.item.repository.MediaFileRepository;
import com.billim.domain.safety.service.MemberRowLock;
import com.billim.domain.safety.service.SanctionService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");   // KST 2026-10-02 09:00
    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final long COMMUNITY = 100L;
    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";
    private static final LocalDate START = LocalDate.of(2026, 10, 5);
    private static final LocalDate END = LocalDate.of(2026, 10, 10);

    @Mock ItemRepository itemRepository;
    @Mock MediaFileRepository mediaFileRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ItemAssembler assembler;
    @Mock ItemAccessPolicy accessPolicy;   // 동네 소속·차단 판정은 Mock
    @Mock CommunityPlaceRepository placeRepository;
    @Mock MemberRowLock memberRowLock;
    @Mock SanctionService sanctions;
    @Mock ItemIdempotency idempotency;

    ItemService service;
    final Category category = new Category(1L, "TOOL", "공구", 1, true);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ItemService(itemRepository, mediaFileRepository, categoryRepository, assembler,
                accessPolicy, placeRepository, memberRowLock, sanctions, idempotency, clock);
        lenient().when(assembler.detail(any(), anyLong())).thenReturn(null);
    }

    // ───── 헬퍼 ─────

    private MediaFile media(long id, long uploader) {
        MediaFile m = MediaFile.temp(uploader, "k/" + id + ".jpg", "image/jpeg", 100, 10, 10, NOW);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    /** 요청한 ID에 해당하는 사진만 돌려주는 저장소 Mock (실제 IN 조회와 같은 동작) */
    private void stubMedia(MediaFile... pool) {
        when(mediaFileRepository.findAllByIdForUpdate(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return Arrays.stream(pool).filter(m -> ids.contains(m.getId())).toList();
        });
    }

    private Item existingItem(long id, long owner, List<Long> imageIds) {
        Item item = Item.create(owner, COMMUNITY, 1L, 10L, "원래 제목", "설명", START, END, NOW);
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "version", 0L);
        if (!imageIds.isEmpty()) {
            item.replaceImages(imageIds, NOW);
        }
        return item;
    }

    private CreateItemRequest createReq(List<String> imageIds, LocalDate start, LocalDate end) {
        return new CreateItemRequest("전동드릴", "설명", "1", "10", start, end, imageIds);
    }

    private UpdateItemRequest updateReq(List<String> imageIds, LocalDate start, LocalDate end, long version) {
        return new UpdateItemRequest("새 제목", null, "1", "10", start, end, imageIds, version);
    }

    private static CommunityPlace place(long id, long communityId, boolean active) {
        CommunityPlace p = CommunityPlace.of(communityId, "정문", BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), null, active);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    /** 장소 10번은 이 동네의 활성 공용 장소 */
    private void stubPlace() {
        when(placeRepository.findByIdAndCommunityId(10L, COMMUNITY)).thenReturn(Optional.of(place(10, COMMUNITY, true)));
    }

    private void stubCreateDependencies() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        stubPlace();
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private Item captureSaved() {
        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    // ───── 등록 ─────

    @Test
    @DisplayName("정상 등록하면 PUBLIC이고 사진이 연결된다")
    void createIsPublicAndAttachesImages() {
        stubCreateDependencies();
        MediaFile m5 = media(5, ME);
        MediaFile m6 = media(6, ME);
        stubMedia(m6, m5);

        service.create(ME, KEY, createReq(List.of("5", "6"), START, END));

        Item saved = captureSaved();
        assertThat(saved.getOwnerId()).isEqualTo(ME);
        assertThat(saved.getCommunityId()).isEqualTo(COMMUNITY);
        assertThat(saved.getVisibility()).isEqualTo(ItemVisibility.PUBLIC);
        assertThat(m5.getStatus()).isEqualTo(MediaStatus.ATTACHED);
        assertThat(m6.getStatus()).isEqualTo(MediaStatus.ATTACHED);
    }

    @Test
    @DisplayName("등록 시 이미지 순서가 sortOrder가 되고 첫 이미지가 대표다")
    void createUsesImageOrderAsSortOrder() {
        stubCreateDependencies();
        stubMedia(media(5, ME), media(6, ME), media(7, ME));

        service.create(ME, KEY, createReq(List.of("7", "5", "6"), START, END));

        List<ItemImage> images = captureSaved().getImages();
        assertThat(images).extracting(ItemImage::getMediaFileId).containsExactly(7L, 5L, 6L);
        assertThat(images).extracting(ItemImage::getSortOrder).containsExactly(0, 1, 2);
        assertThat(images.get(0).isRepresentative()).isTrue();
    }

    @Test
    @DisplayName("카테고리가 없으면 거부한다")
    void createRejectsMissingCategory() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        assertCode(() -> service.create(ME, KEY, createReq(List.of("5"), START, END)), ErrorCode.INVALID_REQUEST);
        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("비활성 카테고리는 거부한다")
    void createRejectsInactiveCategory() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category(1L, "TOOL", "공구", 1, false)));
        assertCode(() -> service.create(ME, KEY, createReq(List.of("5"), START, END)), ErrorCode.INVALID_REQUEST);
    }



    @Test
    @DisplayName("거래 장소는 이 동네의 활성 공용 장소로 저장한다")
    void createStoresPlaceOfOwnCommunity() {
        stubCreateDependencies();
        stubMedia(media(5, ME));

        service.create(ME, KEY, createReq(List.of("5"), START, END));

        assertThat(captureSaved().getPlaceId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("다른 동네 장소나 없는 장소는 400이다")
    void createRejectsPlaceOutsideCommunity() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(placeRepository.findByIdAndCommunityId(10L, COMMUNITY)).thenReturn(Optional.empty());

        assertCode(() -> service.create(ME, KEY, createReq(List.of("5"), START, END)), ErrorCode.INVALID_REQUEST);
        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("운영 중지된 공용 장소는 400이다")
    void createRejectsInactivePlace() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(placeRepository.findByIdAndCommunityId(10L, COMMUNITY)).thenReturn(Optional.of(place(10, COMMUNITY, false)));

        assertCode(() -> service.create(ME, KEY, createReq(List.of("5"), START, END)), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("장소 ID 형식이 틀리면 400이다")
    void createRejectsMalformedPlaceId() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        assertCode(() -> service.create(ME, KEY, new CreateItemRequest("전동드릴", null, "1", "abc", START, END, List.of("5"))),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 거부한다")
    void createRejectsStartAfterEnd() {
        stubCreateDependencies();
        assertCode(() -> service.create(ME, KEY, createReq(List.of("5"), END, START)), ErrorCode.INVALID_REQUEST);
        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미지 ID가 중복되면 거부한다")
    void createRejectsDuplicateImageIds() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        assertCode(() -> service.create(ME, KEY, createReq(List.of("5", "5"), START, END)), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("이미지가 없거나 6개 이상이면 거부한다")
    void createRejectsZeroOrSixImages() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        assertCode(() -> service.create(ME, KEY, createReq(List.of(), START, END)), ErrorCode.INVALID_REQUEST);
        assertCode(() -> service.create(ME, KEY, createReq(List.of("1", "2", "3", "4", "5", "6"), START, END)),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("숫자가 아닌 ID는 거부한다")
    void rejectsMalformedIds() {
        assertCode(() -> IdParser.parse("abc"), ErrorCode.INVALID_REQUEST);
        assertCode(() -> IdParser.parse("0"), ErrorCode.INVALID_REQUEST);
        assertCode(() -> IdParser.parse("9223372036854775808"), ErrorCode.INVALID_REQUEST);
        assertThat(IdParser.parse("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("다른 회원이 올린 이미지는 연결할 수 없다")
    void createRejectsImageOfOtherUploader() {
        stubCreateDependencies();
        stubMedia(media(5, OTHER));
        assertCode(() -> service.create(ME, KEY, createReq(List.of("5"), START, END)), ErrorCode.MEDIA_NOT_ATTACHABLE);
        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("만료된 임시 이미지는 연결할 수 없다")
    void createRejectsExpiredTempImage() {
        stubCreateDependencies();
        MediaFile expired = MediaFile.temp(ME, "k/5.jpg", "image/jpeg", 100, 10, 10, NOW.minusSeconds(25 * 3600));
        ReflectionTestUtils.setField(expired, "id", 5L);
        stubMedia(expired);
        assertCode(() -> service.create(ME, KEY, createReq(List.of("5"), START, END)), ErrorCode.MEDIA_NOT_ATTACHABLE);
    }

    @Test
    @DisplayName("이미 다른 물건에 연결된 이미지는 연결할 수 없다")
    void createRejectsAlreadyAttachedImage() {
        stubCreateDependencies();
        MediaFile attached = media(5, ME);
        attached.attach(ME, NOW);
        stubMedia(attached);
        assertCode(() -> service.create(ME, KEY, createReq(List.of("5"), START, END)), ErrorCode.MEDIA_NOT_ATTACHABLE);
    }

    @Test
    @DisplayName("존재하지 않는 이미지는 연결할 수 없다")
    void createRejectsMissingImage() {
        stubCreateDependencies();
        stubMedia();
        assertCode(() -> service.create(ME, KEY, createReq(List.of("5"), START, END)), ErrorCode.MEDIA_NOT_ATTACHABLE);
    }

    // ───── 수정 ─────

    private void stubUpdateBase(Item item) {
        when(itemRepository.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        stubPlace();
    }

    @Test
    @DisplayName("해당 동네의 유효 소속이 아니면 수정할 수 없다 (403)")
    void updateRequiresValidMembership() {
        Item item = existingItem(7, ME, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));
        doThrow(new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED))
                .when(accessPolicy).requireValidMembership(ME, COMMUNITY);

        assertCode(() -> service.update(ME, 7, updateReq(List.of("5"), START, END, 0)),
                ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
    }

    @Test
    @DisplayName("수정 때도 이 물건 동네의 공용 장소만 허용한다")
    void updateRejectsPlaceOutsideCommunity() {
        Item item = existingItem(7, ME, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(placeRepository.findByIdAndCommunityId(10L, COMMUNITY)).thenReturn(Optional.empty());

        assertCode(() -> service.update(ME, 7, updateReq(List.of("5"), START, END, 0)), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("정상 수정하면 필드와 사진이 바뀐다")
    void updateChangesFieldsAndImages() {
        Item item = existingItem(7, ME, List.of(5L));
        stubUpdateBase(item);
        MediaFile m5 = media(5, ME);
        m5.attach(ME, NOW);
        MediaFile m6 = media(6, ME);
        stubMedia(m5, m6);

        service.update(ME, 7, updateReq(List.of("5", "6"), START.plusDays(1), END.plusDays(1), 0));

        assertThat(item.getTitle()).isEqualTo("새 제목");
        assertThat(item.getDescription()).isNull();   // description 생략은 제거
        assertThat(item.getAvailableStartDate()).isEqualTo(START.plusDays(1));
        assertThat(item.getImages()).extracting(ItemImage::getMediaFileId).containsExactly(5L, 6L);
        assertThat(m6.getStatus()).isEqualTo(MediaStatus.ATTACHED);
    }

    @Test
    @DisplayName("사진을 맨 앞으로 옮기면 그 사진의 sortOrder가 0이 된다")
    void updateMovingImageToFrontMakesItRepresentative() {
        Item item = existingItem(7, ME, List.of(1L, 2L, 5L));
        stubUpdateBase(item);
        List<MediaFile> all = new ArrayList<>();
        for (long id : new long[]{1, 2, 5}) {
            MediaFile m = media(id, ME);
            m.attach(ME, NOW);
            all.add(m);
        }
        stubMedia(all.toArray(MediaFile[]::new));

        service.update(ME, 7, updateReq(List.of("5", "1", "2"), START, END, 0));

        assertThat(item.getImages().get(0).getMediaFileId()).isEqualTo(5L);
        assertThat(item.getImages().get(0).getSortOrder()).isZero();
        assertThat(item.getImages()).extracting(ItemImage::getMediaFileId).containsExactly(5L, 1L, 2L);
        verify(itemRepository, org.mockito.Mockito.atLeast(2)).flush();   // 기존 행을 지우고 flush한 뒤 다시 저장
    }

    @Test
    @DisplayName("제거된 사진은 삭제 표시된다")
    void updateMarksRemovedImageDeleted() {
        Item item = existingItem(7, ME, List.of(5L, 6L));
        stubUpdateBase(item);
        MediaFile m5 = media(5, ME);
        m5.attach(ME, NOW);
        MediaFile m6 = media(6, ME);
        m6.attach(ME, NOW);
        stubMedia(m5, m6);

        service.update(ME, 7, updateReq(List.of("5"), START, END, 0));

        assertThat(item.getImages()).hasSize(1);
        assertThat(m6.getStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(m5.getStatus()).isEqualTo(MediaStatus.ATTACHED);
    }

    @Test
    @DisplayName("버전이 다르면 currentVersion과 함께 충돌한다")
    void updateVersionConflictCarriesCurrentVersion() {
        Item item = existingItem(7, ME, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.update(ME, 7, updateReq(List.of("5"), START, END, 3)))
                .isInstanceOfSatisfying(VersionConflictException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT);
                    assertThat(e.getCurrentVersion()).isZero();
                });
    }

    @Test
    @DisplayName("다른 소유자의 보이는 물건을 수정하면 403이다")
    void updateVisibleItemOfOtherOwnerIsForbidden() {
        Item item = existingItem(7, OTHER, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));
        when(accessPolicy.canView(item, ME)).thenReturn(true);
        assertCode(() -> service.update(ME, 7, updateReq(List.of("5"), START, END, 0)), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("볼 수 없는 타인 물건을 수정하면 404이다")
    void updateInvisibleItemOfOtherOwnerIsNotFound() {
        Item item = existingItem(7, OTHER, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));
        when(accessPolicy.canView(item, ME)).thenReturn(false);
        assertCode(() -> service.update(ME, 7, updateReq(List.of("5"), START, END, 0)), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 물건이거나 삭제된 물건은 404이다")
    void updateMissingOrDeletedItemIsNotFound() {
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertCode(() -> service.update(ME, 7, updateReq(List.of("5"), START, END, 0)), ErrorCode.RESOURCE_NOT_FOUND);

        Item deleted = existingItem(8, ME, List.of(5L));
        deleted.delete(NOW);
        when(itemRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(deleted));
        assertCode(() -> service.update(ME, 8, updateReq(List.of("5"), START, END, 0)), ErrorCode.RESOURCE_NOT_FOUND);
    }




    @Test
    @DisplayName("수정할 때도 이미지 개수와 중복을 검증한다")
    void updateValidatesImageCountAndDuplicates() {
        Item item = existingItem(7, ME, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));
        assertCode(() -> service.update(ME, 7, updateReq(List.of("5", "5"), START, END, 0)), ErrorCode.INVALID_REQUEST);
        assertCode(() -> service.update(ME, 7, updateReq(List.of(), START, END, 0)), ErrorCode.INVALID_REQUEST);
    }

    // ───── 공개·숨김 ─────

    @Test
    @DisplayName("숨김 전환은 동네 소속과 무관하게 허용한다")
    void hideIsAllowedWithoutMembership() {
        Item item = existingItem(7, ME, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));

        service.changeVisibility(ME, 7, new VisibilityChangeRequest(0L, ItemVisibility.HIDDEN));

        assertThat(item.getVisibility()).isEqualTo(ItemVisibility.HIDDEN);
        verify(accessPolicy, never()).requireActiveCommunityId(anyLong());
        verify(accessPolicy, never()).requireValidMembership(anyLong(), anyLong());
    }

    @Test
    @DisplayName("해당 동네 유효 소속이면 숨긴 물건을 다시 공개할 수 있다")
    void ownerWithValidMembershipCanRepublish() {
        Item item = existingItem(7, ME, List.of(5L));
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));

        service.changeVisibility(ME, 7, new VisibilityChangeRequest(0L, ItemVisibility.PUBLIC));

        assertThat(item.getVisibility()).isEqualTo(ItemVisibility.PUBLIC);
        verify(accessPolicy).requireValidMembership(ME, COMMUNITY);
    }

    @Test
    @DisplayName("해당 동네 유효 소속이 아니면 다시 공개할 수 없다 (403)")
    void republishRequiresValidMembership() {
        Item item = existingItem(7, ME, List.of(5L));
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));
        doThrow(new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED))
                .when(accessPolicy).requireValidMembership(ME, COMMUNITY);

        assertCode(() -> service.changeVisibility(ME, 7, new VisibilityChangeRequest(0L, ItemVisibility.PUBLIC)),
                ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
        assertThat(item.getVisibility()).isEqualTo(ItemVisibility.HIDDEN);
    }


    @Test
    @DisplayName("공개 변경도 버전 충돌을 검사한다")
    void changeVisibilityChecksVersion() {
        Item item = existingItem(7, ME, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.changeVisibility(ME, 7, new VisibilityChangeRequest(9L, ItemVisibility.HIDDEN)))
                .isInstanceOf(VersionConflictException.class);
    }

    // ───── 삭제 ─────

    @Test
    @DisplayName("정상 소프트 삭제한다")
    void deleteIsSoft() {
        Item item = existingItem(7, ME, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));

        service.delete(ME, 7, 0);

        assertThat(item.getVisibility()).isEqualTo(ItemVisibility.DELETED);
        assertThat(item.getDeletedAt()).isEqualTo(NOW);
        verify(itemRepository, never()).delete(any(Item.class));   // 물리 삭제 아님
        verify(itemRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("다른 소유자의 물건은 삭제할 수 없다")
    void deleteOthersItemIsForbidden() {
        Item item = existingItem(7, OTHER, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));
        when(accessPolicy.canView(item, ME)).thenReturn(true);
        assertCode(() -> service.delete(ME, 7, 0), ErrorCode.FORBIDDEN);
        assertThat(item.isDeleted()).isFalse();
    }


    @Test
    @DisplayName("삭제도 버전 충돌을 검사하고 이미 삭제된 물건은 404이다")
    void deleteChecksVersionAndDeletedIsNotFound() {
        Item item = existingItem(7, ME, List.of(5L));
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.delete(ME, 7, 4)).isInstanceOf(VersionConflictException.class);

        item.delete(NOW);
        assertCode(() -> service.delete(ME, 7, 0), ErrorCode.RESOURCE_NOT_FOUND);
    }

    // ───── 상세 ─────

    @Test
    @DisplayName("접근할 수 없는 물건 상세는 404이다")
    void getInaccessibleItemIsNotFound() {
        Item item = existingItem(7, OTHER, List.of(5L));
        when(itemRepository.findById(7L)).thenReturn(Optional.of(item));
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(accessPolicy.canView(item, ME)).thenReturn(false);
        assertCode(() -> service.get(ME, 7), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 물건 상세는 404이다")
    void getMissingItemIsNotFound() {
        when(itemRepository.findById(7L)).thenReturn(Optional.empty());
        assertCode(() -> service.get(ME, 7), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("본인 물건 상세는 소속 확인 없이 조회한다")
    void getOwnItemSkipsMembershipCheck() {
        Item item = existingItem(7, ME, List.of(5L));
        when(itemRepository.findById(7L)).thenReturn(Optional.of(item));
        when(accessPolicy.canView(item, ME)).thenReturn(true);
        service.get(ME, 7);
        verify(accessPolicy, never()).requireActiveCommunityId(anyLong());
    }

    // ───── 검색 ─────

    private ItemSearchCondition cond(LocalDate s, LocalDate e, ItemSort sort, int page, int size) {
        return new ItemSearchCondition(null, null, s, e, null, sort, page, size);
    }

    @Test
    @DisplayName("검색 시작일만 입력하면 400이다")
    void searchWithOnlyStartDateIsInvalid() {
        assertCode(() -> service.search(ME, cond(START, null, ItemSort.LATEST, 0, 20)), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("검색 종료일만 입력하면 400이다")
    void searchWithOnlyEndDateIsInvalid() {
        assertCode(() -> service.search(ME, cond(null, END, ItemSort.LATEST, 0, 20)), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("검색 시작일이 종료일보다 늦으면 400이다")
    void searchWithReversedPeriodIsInvalid() {
        assertCode(() -> service.search(ME, cond(END, START, ItemSort.LATEST, 0, 20)), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("검색 시작일이 KST 오늘보다 이전이면 400이다")
    void searchStartingBeforeTodayKstIsInvalid() {
        assertCode(() -> service.search(ME, cond(LocalDate.of(2026, 10, 1), END, ItemSort.LATEST, 0, 20)),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("검색 기간이 30일을 넘으면 400이다")
    void searchLongerThan30DaysIsInvalid() {
        LocalDate s = LocalDate.of(2026, 10, 2);
        assertCode(() -> service.search(ME, cond(s, s.plusDays(30), ItemSort.LATEST, 0, 20)), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("검색 페이지가 음수이면 400이다")
    void searchWithNegativePageIsInvalid() {
        assertCode(() -> service.search(ME, cond(null, null, ItemSort.LATEST, -1, 20)), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("검색 페이지 크기가 50을 넘거나 1 미만이면 400이다")
    void searchWithOutOfRangeSizeIsInvalid() {
        assertCode(() -> service.search(ME, cond(null, null, ItemSort.LATEST, 0, 51)), ErrorCode.INVALID_REQUEST);
        assertCode(() -> service.search(ME, cond(null, null, ItemSort.LATEST, 0, 0)), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("검색은 같은 동네의 공개 물건을 최신순(createdAt, id 내림차순)으로 페이지 조회한다")
    void searchLatestSortsByCreatedAtThenIdDesc() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(itemRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        ItemPageResponse res = service.search(ME, cond(START, END, ItemSort.LATEST, 1, 10));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(itemRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("createdAt: DESC,id: DESC");
        assertThat(res.items()).isEmpty();
    }

    @Test
    @DisplayName("정렬을 생략하면 최신순으로 검색한다")
    void searchWithoutSortDefaultsToLatest() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(itemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.search(ME, cond(null, null, null, 0, 20));

        verify(itemRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("거리순 정렬은 동네 연동 전에는 다른 정렬로 바꾸지 않고 503으로 거부한다 (TODO(E))")
    void searchNearestIsUnavailableUntilCommunityIsLinked() {
        assertCode(() -> service.search(ME, cond(null, null, ItemSort.NEAREST, 0, 20)), ErrorCode.DEPENDENCY_UNAVAILABLE);
        verify(itemRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("검색은 동네 공개 물건 소유자 중 차단 관계인 회원을 조회 전에 제외 조건으로 넣는다")
    void searchExcludesBlockedOwnersBeforePaging() {
        when(accessPolicy.requireActiveCommunityId(ME)).thenReturn(COMMUNITY);
        when(itemRepository.findPublicOwnerIds(COMMUNITY)).thenReturn(List.of(2L, 3L, 4L));
        when(accessPolicy.findBlockedOwners(ME, List.of(2L, 3L, 4L))).thenReturn(Set.of(3L));
        when(itemRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(ME, cond(null, null, ItemSort.LATEST, 0, 20));

        verify(accessPolicy).findBlockedOwners(ME, List.of(2L, 3L, 4L));
        verify(itemRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ───── 재전송·제재 ─────

    @Test
    @DisplayName("같은 키로 이미 성공한 등록은 저장된 응답을 그대로 돌려주고 다시 저장하지 않는다")
    void createReplaysStoredResponseForSameKey() {
        ItemDetailResponse stored = mock(ItemDetailResponse.class);
        when(idempotency.hashOf(any())).thenReturn("h");
        when(idempotency.replay(ME, ItemIdempotency.CREATE_ITEM, KEY, "h", 201, ItemDetailResponse.class, NOW))
                .thenReturn(Optional.of(stored));

        assertThat(service.create(ME, KEY, createReq(List.of("5"), START, END))).isSameAs(stored);

        verify(memberRowLock).lock(ME);
        verify(itemRepository, never()).saveAndFlush(any());
        verify(idempotency, never()).record(anyLong(), anyString(), anyString(), anyString(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("처음 등록은 회원 행을 잠근 뒤 저장하고 성공 응답을 키와 함께 보관한다")
    void createRecordsResponseForReplay() {
        stubCreateDependencies();
        stubMedia(media(5, ME));
        ItemDetailResponse response = mock(ItemDetailResponse.class);
        when(assembler.detail(any(), anyLong())).thenReturn(response);
        when(idempotency.hashOf(any())).thenReturn("h");

        service.create(ME, KEY, createReq(List.of("5"), START, END));

        InOrder order = inOrder(memberRowLock, itemRepository, idempotency);
        order.verify(memberRowLock).lock(ME);
        order.verify(itemRepository).saveAndFlush(any());
        order.verify(idempotency).record(ME, ItemIdempotency.CREATE_ITEM, KEY, "h", 201, response, NOW);
    }

    @Test
    @DisplayName("제재 중인 회원은 물건을 등록할 수 없다 (403 MEMBER_RESTRICTED)")
    void restrictedMemberCannotCreate() {
        when(sanctions.isRestricted(ME)).thenReturn(true);

        assertCode(() -> service.create(ME, KEY, createReq(List.of("5"), START, END)), ErrorCode.MEMBER_RESTRICTED);
        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("제재 중인 회원은 숨긴 물건을 다시 공개할 수 없다 (숨김 전환은 허용)")
    void restrictedMemberCannotRepublish() {
        Item item = existingItem(7, ME, List.of(5L));
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        when(itemRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(item));
        when(sanctions.isRestricted(ME)).thenReturn(true);

        assertCode(() -> service.changeVisibility(ME, 7, new VisibilityChangeRequest(0L, ItemVisibility.PUBLIC)),
                ErrorCode.MEMBER_RESTRICTED);
        assertThat(item.getVisibility()).isEqualTo(ItemVisibility.HIDDEN);
    }
}
