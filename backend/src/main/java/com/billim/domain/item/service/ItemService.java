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
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.item.repository.ItemSpecifications;
import com.billim.domain.item.repository.MediaFileRepository;
import com.billim.domain.safety.service.MemberRowLock;
import com.billim.domain.safety.service.SanctionService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ItemService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_SEARCH_DAYS = 30;
    private static final int MAX_KEYWORD = 100;

    private final ItemRepository itemRepository;
    private final MediaFileRepository mediaFileRepository;
    private final CategoryRepository categoryRepository;
    private final ItemAssembler assembler;
    private final ItemAccessPolicy accessPolicy;
    private final CommunityPlaceRepository placeRepository;
    private final MemberRowLock memberRowLock;
    private final SanctionService sanctions;
    private final ItemIdempotency idempotency;
    private final Clock clock;

    public ItemService(ItemRepository itemRepository, MediaFileRepository mediaFileRepository,
                       CategoryRepository categoryRepository, ItemAssembler assembler,
                       ItemAccessPolicy accessPolicy, CommunityPlaceRepository placeRepository,
                       MemberRowLock memberRowLock, SanctionService sanctions, ItemIdempotency idempotency,
                       Clock clock) {
        this.itemRepository = itemRepository;
        this.mediaFileRepository = mediaFileRepository;
        this.categoryRepository = categoryRepository;
        this.assembler = assembler;
        this.accessPolicy = accessPolicy;
        this.placeRepository = placeRepository;
        this.memberRowLock = memberRowLock;
        this.sanctions = sanctions;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    // ───────── 등록 ─────────

    @Transactional
    public ItemDetailResponse create(long memberId, String idempotencyKey, CreateItemRequest req) {
        Instant now = clock.instant();
        // 같은 회원의 동시 요청 직렬화 후 재전송 확인. 같은 키로 이미 성공했으면 처음 응답 그대로
        memberRowLock.lock(memberId);
        String requestHash = idempotency.hashOf(req);
        Optional<ItemDetailResponse> replay = idempotency.replay(memberId, ItemIdempotency.CREATE_ITEM,
                idempotencyKey, requestHash, 201, ItemDetailResponse.class, now);
        if (replay.isPresent()) {
            return replay.get();
        }
        requireNotRestricted(memberId);
        long communityId = accessPolicy.requireActiveCommunityId(memberId);   // 선택한 동네가 없으면 403
        List<Long> imageIds = parseImageIds(req.imageIds());
        Category category = requireCategory(IdParser.parse(req.categoryId()));
        long placeId = requirePlace(IdParser.parse(req.placeId()), communityId);

        Item item = Item.create(memberId, communityId, category.getId(), placeId,
                req.title(), req.description(), req.availableStartDate(), req.availableEndDate(), now);
        attachMedia(memberId, imageIds, Set.of(), now);
        item.replaceImages(imageIds, now);
        try {
            itemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException e) {
            // 같은 사진의 동시 연결 등 unique 위반
            throw new BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE);
        }
        ItemDetailResponse response = assembler.detail(item, memberId);
        idempotency.record(memberId, ItemIdempotency.CREATE_ITEM, idempotencyKey, requestHash, 201, response, now);
        return response;
    }

    // ───────── 상세 ─────────

    @Transactional(readOnly = true)
    public ItemDetailResponse get(long memberId, long itemId) {
        Item item = itemRepository.findById(itemId).orElseThrow(ItemService::notFound);
        if (!item.isOwnedBy(memberId)) {
            accessPolicy.requireActiveCommunityId(memberId);   // 타인 물건은 동네 소속이 필요 (없으면 403)
        }
        if (!accessPolicy.canView(item, memberId)) {
            throw notFound();
        }
        return assembler.detail(item, memberId);
    }

    // ───────── 수정 ─────────

    @Transactional
    public ItemDetailResponse update(long memberId, long itemId, UpdateItemRequest req) {
        Instant now = clock.instant();
        Item item = loadForCommand(memberId, itemId);
        accessPolicy.requireValidMembership(memberId, item.getCommunityId());   // 소유자·유효 해당 동네만 (B_021)
        requireVersion(item, req.expectedVersion());

        List<Long> imageIds = parseImageIds(req.imageIds());
        Category category = requireCategory(IdParser.parse(req.categoryId()));
        long placeId = requirePlace(IdParser.parse(req.placeId()), item.getCommunityId());
        // TODO(C): 진행 중인 요청·거래(REQUESTED/APPROVED/ACTIVE)가 있으면
        //          ① 가능 기간이 그 기간을 모두 포함해야 하고(아니면 409 ITEM_HAS_OPEN_RENTALS),
        //          ② 사진을 제거할 수 없다(409 MEDIA_IN_USE). 연동 전에는 검사하지 않는다.

        List<Long> currentIds = item.getImages().stream().map(ItemImage::getMediaFileId).toList();
        Set<Long> removed = new LinkedHashSet<>(currentIds);
        removed.removeAll(imageIds);

        item.update(category.getId(), placeId, req.title(), req.description(),
                req.availableStartDate(), req.availableEndDate(), now);

        if (!currentIds.equals(imageIds)) {
            attachMedia(memberId, imageIds, Set.copyOf(currentIds), now);
            // unique (item_id, sort_order) 충돌을 피하려고 기존 행을 지우고 flush한 뒤 다시 만든다.
            item.clearImages();
            itemRepository.flush();
            item.replaceImages(imageIds, now);
            if (!removed.isEmpty()) {
                mediaFileRepository.findAllByIdForUpdate(removed).forEach(m -> m.markDeleted(now));
            }
        }
        try {
            itemRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE);
        }
        return assembler.detail(item, memberId);
    }

    // ───────── 공개·공개 중지 ─────────

    @Transactional
    public ItemDetailResponse changeVisibility(long memberId, long itemId, VisibilityChangeRequest req) {
        Instant now = clock.instant();
        Item item = loadForCommand(memberId, itemId);
        requireVersion(item, req.expectedVersion());
        // PUBLIC 복원은 해당 동네 유효 소속 필요. HIDDEN 전환은 인증 만료·소속 변경에도 정리 목적으로 허용 (B_022)
        if (req.visibility() == ItemVisibility.PUBLIC) {
            accessPolicy.requireValidMembership(memberId, item.getCommunityId());
            requireNotRestricted(memberId);
        }
        item.changeVisibility(req.visibility(), now);
        itemRepository.flush();
        return assembler.detail(item, memberId);
    }

    // ───────── 논리 삭제 ─────────

    @Transactional
    public void delete(long memberId, long itemId, long expectedVersion) {
        Instant now = clock.instant();
        Item item = loadForCommand(memberId, itemId);
        requireVersion(item, expectedVersion);
        // TODO(C): 진행 중인 요청·거래(REQUESTED/APPROVED/ACTIVE)가 있으면 409 ITEM_HAS_OPEN_RENTALS. 연동 전에는 검사하지 않는다.
        item.delete(now);   // visibility=DELETED, deleted_at. 행·사진은 물리 삭제하지 않는다.
        itemRepository.flush();
    }

    // ───────── 검색 ─────────

    @Transactional(readOnly = true)
    public ItemPageResponse search(long memberId, ItemSearchCondition cond) {
        ItemSort sort = cond.sort() == null ? ItemSort.LATEST : cond.sort();
        validateSearch(cond);
        if (sort == ItemSort.NEAREST) {
            // TODO(E): 동네 중심과 장소 좌표가 연동되면 거리순 정렬을 구현한다. 연동 전에는 다른 정렬로 바꿔 응답하지 않는다.
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "거리순 정렬은 동네 연동 후 사용할 수 있습니다.");
        }
        long communityId = accessPolicy.requireActiveCommunityId(memberId);   // 선택한 동네가 없으면 403

        // 차단 관계(양방향) 소유자의 물건 제외. 페이지 수가 맞도록 조회 전에 조건으로 거름
        Set<Long> blockedOwners = accessPolicy.findBlockedOwners(memberId, itemRepository.findPublicOwnerIds(communityId));
        // TODO(C): 선택 기간에 승인·진행 중인 대여가 있는 물건을 제외하고 availableForRange를 채운다.
        //          연동 전에는 물건의 대여 가능 기간이 선택 기간을 포함하는지만 본다.
        Specification<Item> spec = ItemSpecifications.allOf(
                ItemSpecifications.community(communityId),
                ItemSpecifications.publicOnly(),
                ItemSpecifications.ownerNotIn(blockedOwners),
                ItemSpecifications.category(cond.categoryId()),
                ItemSpecifications.place(cond.placeId()),
                ItemSpecifications.titleContains(cond.keyword()),
                ItemSpecifications.availableCovers(cond.startDate(), cond.endDate()));

        Page<Item> page = itemRepository.findAll(spec, PageRequest.of(cond.page(), cond.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new ItemPageResponse(assembler.summaries(page.getContent()),
                cond.page(), cond.size(), page.getTotalElements(), page.hasNext());
    }

    /** 검색 조건 검증 (API 명세 B_017). 위반은 모두 400 INVALID_REQUEST */
    void validateSearch(ItemSearchCondition c) {
        if (c.page() < 0) {
            throw invalid("page는 0 이상이어야 합니다.");
        }
        if (c.size() < 1 || c.size() > ItemSearchCondition.MAX_SIZE) {
            throw invalid("size는 1 이상 50 이하여야 합니다.");
        }
        if (c.keyword() != null && c.keyword().trim().length() > MAX_KEYWORD) {
            throw invalid("검색어는 100자 이하여야 합니다.");
        }
        if ((c.startDate() == null) != (c.endDate() == null)) {
            throw invalid("시작일과 종료일은 함께 입력해야 합니다.");
        }
        if (c.startDate() != null) {
            if (c.startDate().isAfter(c.endDate())) {
                throw invalid("시작일이 종료일보다 늦을 수 없습니다.");
            }
            LocalDate todayKst = LocalDate.now(clock.withZone(KST));
            if (c.startDate().isBefore(todayKst)) {
                throw invalid("시작일은 오늘 이후여야 합니다.");
            }
            if (ChronoUnit.DAYS.between(c.startDate(), c.endDate()) + 1 > MAX_SEARCH_DAYS) {
                throw invalid("검색 기간은 최대 30일입니다.");
            }
        }
    }

    // ───────── 내부 ─────────

    /** 명령(수정·삭제·공개 변경)용 로드. 행 잠금. 소유자가 아니면 보이는 대상은 403, 아니면 404 */
    private Item loadForCommand(long memberId, long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .filter(i -> !i.isDeleted())
                .orElseThrow(ItemService::notFound);
        if (!item.isOwnedBy(memberId)) {
            if (accessPolicy.canView(item, memberId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            throw notFound();
        }
        return item;
    }

    // 유효 제재 중이면 등록·공개 복원 불가 (B_019 제한 없음, B_022)
    private void requireNotRestricted(long memberId) {
        if (sanctions.isRestricted(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_RESTRICTED);
        }
    }

    // 거래 장소는 해당 동네의 활성 공용 장소만 (B_019 다른 동네 장소 금지, 정책 5 거래 장소)
    private long requirePlace(long placeId, long communityId) {
        return placeRepository.findByIdAndCommunityId(placeId, communityId)
                .filter(CommunityPlace::isActive)
                .map(CommunityPlace::getId)
                .orElseThrow(() -> invalid("유효하지 않은 거래 장소입니다."));
    }

    private static void requireVersion(Item item, long expectedVersion) {
        if (item.getVersion() != expectedVersion) {
            throw new VersionConflictException(item.getVersion());
        }
    }

    private Category requireCategory(long categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(Category::isActive)
                .orElseThrow(() -> invalid("유효하지 않은 카테고리입니다."));
    }

    private static List<Long> parseImageIds(List<String> raw) {
        if (raw == null) {
            throw invalid("사진은 1장 이상 5장 이하로 등록해 주세요.");
        }
        List<Long> ids = raw.stream().map(IdParser::parse).toList();
        Item.validateImageIds(ids);
        return ids;
    }

    /**
     * 요청 사진 잠금·검증 후 연결.
     * 이미 이 물건에 연결된 사진(ownedMediaIds)은 유지, 그 외는 요청 회원의 만료 전 TEMP만 허용. 아니면 MEDIA_NOT_ATTACHABLE
     */
    private void attachMedia(long memberId, List<Long> ids, Set<Long> ownedMediaIds, Instant now) {
        Map<Long, MediaFile> found = new HashMap<>();
        mediaFileRepository.findAllByIdForUpdate(ids).forEach(m -> found.put(m.getId(), m));
        for (Long id : ids) {
            MediaFile m = found.get(id);
            if (m == null) {
                throw new BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE);
            }
            if (!ownedMediaIds.contains(id)) {
                m.attach(memberId, now);
            }
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, message);
    }
}
