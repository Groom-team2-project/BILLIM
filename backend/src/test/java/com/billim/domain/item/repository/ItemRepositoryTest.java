package com.billim.domain.item.repository;

import com.billim.domain.item.entity.Category;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemImage;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.entity.MediaFile;
import com.billim.domain.member.entity.Member;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 MySQL(Testcontainers)에서 Flyway 스키마·JPA 매핑(ddl-auto validate)·검색 조건·인덱스 후보를 확인한다.
 * Docker가 필요하다.
 */
@DataJpaTest
@Import(ItemRepositoryTest.MySql.class)
class ItemRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));   // compose.yaml과 같은 버전
        }
    }

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final long OWNER = 1L;
    private static final long COMMUNITY = 100L;
    private static final LocalDate START = LocalDate.of(2026, 10, 5);
    private static final LocalDate END = LocalDate.of(2026, 10, 20);

    @Autowired EntityManager em;
    @Autowired ItemRepository itemRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ItemImageRepository itemImageRepository;

    private long seq = 0;

    private long categoryId() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc().getFirst().getId();
    }

    private MediaFile media(long uploader) {
        MediaFile m = MediaFile.temp(uploader, "test/" + (++seq) + ".jpg", "image/jpeg", 100, 10, 10, NOW);
        em.persist(m);
        return m;
    }

    private Item item(long community, String title, String description, LocalDate start, LocalDate end, Instant at) {
        MediaFile m = media(OWNER);
        m.attach(OWNER, at);
        Item item = Item.create(OWNER, community, categoryId(), 10L, title, description, start, end, at);
        item.replaceImages(List.of(m.getId()), at);
        em.persist(item);
        return item;
    }

    private Item item(String title) {
        return item(COMMUNITY, title, null, START, END, NOW.plusSeconds(++seq));
    }

    private List<Long> search(String keyword, LocalDate start, LocalDate end) {
        return itemRepository.findAll(ItemSpecifications.allOf(
                        ItemSpecifications.community(COMMUNITY),
                        ItemSpecifications.publicOnly(),
                        ItemSpecifications.titleContains(keyword),
                        ItemSpecifications.availableCovers(start, end)),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).stream().map(Item::getId).toList();
    }

    @Test
    @DisplayName("Flyway 카테고리 기준 데이터 10개를 sortOrder 순으로 조회한다")
    void categorySeedIsOrdered() {
        List<Category> categories = categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        assertThat(categories).extracting(Category::getCode).containsExactly(
                "TOOL", "CAMP", "TRAVEL", "BABY", "MUSIC", "SPORT", "KITCHEN", "CLEAN", "LIFE", "OTHER");
    }

    @Test
    @DisplayName("저장하면 version 0에서 시작하고 수정 flush마다 증가한다")
    void versionStartsAtZeroAndIncrements() {
        Item item = item("전동드릴");
        em.flush();
        assertThat(item.getVersion()).isZero();

        item.update(item.getCategoryId(), 10L, "충전 드릴", null, START, END, NOW.plusSeconds(100));
        em.flush();
        assertThat(item.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("사진 순서를 바꿔 다시 저장하면 맨 앞 사진이 sort_order 0이 된다")
    void reorderPersistsNewSortOrder() {
        MediaFile a = media(OWNER);
        MediaFile b = media(OWNER);
        MediaFile c = media(OWNER);
        Item item = Item.create(OWNER, COMMUNITY, categoryId(), 10L, "텐트", null, START, END, NOW);
        item.replaceImages(List.of(a.getId(), b.getId(), c.getId()), NOW);
        em.persist(item);
        em.flush();

        item.clearImages();
        itemRepository.flush();   // ItemService.update와 같은 순서: 기존 행 삭제 → flush → 재생성
        item.replaceImages(List.of(c.getId(), a.getId(), b.getId()), NOW);
        em.flush();
        em.clear();

        Item reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getImages()).extracting(ItemImage::getMediaFileId)
                .containsExactly(c.getId(), a.getId(), b.getId());
        assertThat(reloaded.getImages().getFirst().getSortOrder()).isZero();
        assertThat(itemImageRepository.findThumbnails(Set.of(item.getId())))
                .singleElement().satisfies(t -> assertThat(t.getMediaFileId()).isEqualTo(c.getId()));
    }

    @Test
    @DisplayName("같은 사진은 두 물건에 연결할 수 없다(UNIQUE media_file_id)")
    void sameMediaCannotBeAttachedTwice() {
        MediaFile shared = media(OWNER);
        Item first = Item.create(OWNER, COMMUNITY, categoryId(), 10L, "하나", null, START, END, NOW);
        first.replaceImages(List.of(shared.getId()), NOW);
        em.persist(first);
        em.flush();

        Item second = Item.create(OWNER, COMMUNITY, categoryId(), 10L, "둘", null, START, END, NOW);
        second.replaceImages(List.of(shared.getId()), NOW);
        assertThatThrownBy(() -> itemRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("검색은 같은 동네의 공개 물건만 최신순으로 찾고 숨김·삭제·다른 동네는 제외한다")
    void searchExcludesHiddenDeletedAndOtherCommunity() {
        Item visibleOld = item("드릴 A");
        Item visibleNew = item("드릴 B");
        Item hidden = item("드릴 C");
        hidden.changeVisibility(ItemVisibility.HIDDEN, NOW);
        Item deleted = item("드릴 D");
        deleted.delete(NOW);
        item(999L, "드릴 E", null, START, END, NOW);
        em.flush();

        assertThat(search(null, null, null)).containsExactly(visibleNew.getId(), visibleOld.getId());
        assertThat(itemRepository.findById(deleted.getId())).get()
                .satisfies(i -> assertThat(i.getDeletedAt()).isNotNull());   // 행은 남는다(논리 삭제)
    }

    @Test
    @DisplayName("키워드는 제목 부분 일치만 찾고 설명은 찾지 않으며 LIKE 특수문자는 글자로 취급한다")
    void keywordMatchesTitleOnlyAndEscapesWildcards() {
        Item drill = item(COMMUNITY, "충전식 전동드릴", "설명", START, END, NOW);
        item(COMMUNITY, "캠핑 의자", "드릴 포함 아님", START, END, NOW);
        Item percent = item(COMMUNITY, "100% 면 담요", null, START, END, NOW);
        item(COMMUNITY, "1000 면 담요", null, START, END, NOW);
        em.flush();

        assertThat(search("드릴", null, null)).containsExactly(drill.getId());
        assertThat(search("0%", null, null)).containsExactly(percent.getId());
    }

    @Test
    @DisplayName("기간 검색은 대여 가능 기간이 선택 기간 전체를 포함하는 물건만 찾는다")
    void periodSearchRequiresFullCoverage() {
        Item covers = item(COMMUNITY, "포함", null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), NOW);
        item(COMMUNITY, "앞부분만", null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9), NOW);
        Item exact = item(COMMUNITY, "딱 맞음", null, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12), NOW);
        em.flush();

        assertThat(search(null, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12)))
                .containsExactlyInAnyOrder(covers.getId(), exact.getId());
    }

    /**
     * 검색 쿼리의 인덱스 후보 확인용. 테스트 전용 EXPLAIN이며 운영 코드에는 Native Query가 없다.
     * 실제 선택 인덱스(key)는 데이터 분포에 따라 바뀌므로 후보(possible_keys)만 검증하고, 성능 판단은 고정 seed로 측정한다.
     */
    @Test
    @DisplayName("동네·공개 상태 검색 조건은 ERD의 items 인덱스를 후보로 사용한다")
    void searchQueryHasItemIndexCandidates() {
        item("드릴");
        em.flush();
        @SuppressWarnings("unchecked")
        List<Object[]> plan = em.createNativeQuery("""
                        EXPLAIN SELECT i.id FROM items i
                        WHERE i.community_id = ?1 AND i.visibility = 'PUBLIC'
                        ORDER BY i.created_at DESC, i.id DESC LIMIT 20""")
                .setParameter(1, COMMUNITY)
                .getResultList();
        String possibleKeys = String.valueOf(plan.getFirst()[5]);
        assertThat(possibleKeys).contains("idx_items_community_visibility_created");
    }

    private Item itemOf(long owner, long community, String title, ItemVisibility visibility) {
        MediaFile m = media(owner);
        m.attach(owner, NOW);
        Item item = Item.create(owner, community, categoryId(), 10L, title, null, START, END, NOW.plusSeconds(++seq));
        item.replaceImages(List.of(m.getId()), NOW);
        if (visibility == ItemVisibility.HIDDEN) {
            item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        }
        em.persist(item);
        return item;
    }

    @Test
    @DisplayName("차단 판정 후보는 동네의 공개 물건 소유자만이고, ownerNotIn으로 해당 소유자 물건을 검색에서 제외한다")
    void publicOwnerCandidatesAndOwnerExclusion() {
        Item fromTwo = itemOf(2L, COMMUNITY, "드릴", ItemVisibility.PUBLIC);
        itemOf(3L, COMMUNITY, "텐트", ItemVisibility.PUBLIC);
        itemOf(4L, COMMUNITY, "숨김", ItemVisibility.HIDDEN);
        itemOf(5L, 999L, "다른 동네", ItemVisibility.PUBLIC);
        em.flush();

        assertThat(itemRepository.findPublicOwnerIds(COMMUNITY)).containsExactlyInAnyOrder(2L, 3L);

        List<Long> visible = itemRepository.findAll(ItemSpecifications.allOf(
                        ItemSpecifications.community(COMMUNITY),
                        ItemSpecifications.publicOnly(),
                        ItemSpecifications.ownerNotIn(Set.of(3L))),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).stream().map(Item::getId).toList();
        assertThat(visible).containsExactly(fromTwo.getId());
    }

    private Item itemAtPlace(long placeId, String title, ItemVisibility visibility) {
        MediaFile m = media(OWNER);
        m.attach(OWNER, NOW);
        Item item = Item.create(OWNER, COMMUNITY, categoryId(), placeId, title, null, START, END, NOW.plusSeconds(++seq));
        item.replaceImages(List.of(m.getId()), NOW);
        if (visibility == ItemVisibility.HIDDEN) {
            item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        }
        em.persist(item);
        return item;
    }

    @Test
    @DisplayName("지도 집계는 검색 조건에 맞는 물건 수를 장소별로 센다 (숨김 제외)")
    void countByPlaceGroupsMatchingItems() {
        itemAtPlace(10L, "드릴", ItemVisibility.PUBLIC);
        itemAtPlace(10L, "텐트", ItemVisibility.PUBLIC);
        itemAtPlace(20L, "의자", ItemVisibility.PUBLIC);
        itemAtPlace(20L, "숨김", ItemVisibility.HIDDEN);
        em.flush();

        Map<Long, Long> counts = itemRepository.countByPlace(ItemSpecifications.allOf(
                ItemSpecifications.community(COMMUNITY), ItemSpecifications.publicOnly()));

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(10L, 2L, 20L, 1L));
    }

    @Test
    @DisplayName("가까운 순은 장소 거리 오름차순, 같은 거리는 id 내림차순, 거리를 모르는 장소는 맨 뒤다")
    void orderByPlaceDistanceSortsNearestFirst() {
        Item far = itemAtPlace(10L, "먼 곳", ItemVisibility.PUBLIC);
        Item nearOld = itemAtPlace(20L, "가까운 곳 1", ItemVisibility.PUBLIC);
        Item nearNew = itemAtPlace(20L, "가까운 곳 2", ItemVisibility.PUBLIC);
        Item unknown = itemAtPlace(30L, "거리 모름", ItemVisibility.PUBLIC);
        em.flush();

        Page<Item> page = itemRepository.findAll(ItemSpecifications.allOf(
                        ItemSpecifications.community(COMMUNITY), ItemSpecifications.publicOnly(),
                        ItemSpecifications.orderByPlaceDistance(Map.of(10L, 300, 20L, 100))),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Item::getId)
                .containsExactly(nearNew.getId(), nearOld.getId(), far.getId(), unknown.getId());
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    // ───── 기간 검색: 대여 점유 제외 ─────

    private long member(String name) {
        Member m = Member.register(name);
        em.persist(m);
        return m.getId();
    }

    private void rental(Item item, long owner, long borrower, RentalStatus status,
                        LocalDate start, LocalDate end, Instant dueAt) {
        Rental r = Rental.request(item.getId(), owner, borrower, COMMUNITY, start, end,
                NOW.plusSeconds(86400), dueAt, item.getTitle(), 10L, "정문");
        ReflectionTestUtils.setField(r, "status", status);
        em.persist(r);
    }

    @Test
    @DisplayName("기간 검색은 승인·대여 중·반납 완료와 겹치는 물건, 연체 중인 물건을 빼고 요청 대기 겹침은 허용한다")
    void rentalRangeExcludesOccupiedAndOverdueItems() {
        long owner = member("소유자");
        long borrower = member("이웃");
        LocalDate from = LocalDate.of(2026, 10, 10);
        LocalDate to = LocalDate.of(2026, 10, 12);
        Instant later = NOW.plusSeconds(30L * 86400);

        Item approved = itemOf(owner, COMMUNITY, "승인 겹침", ItemVisibility.PUBLIC);
        Item boundary = itemOf(owner, COMMUNITY, "경계 겹침", ItemVisibility.PUBLIC);
        Item returned = itemOf(owner, COMMUNITY, "반납 완료 겹침", ItemVisibility.PUBLIC);
        Item overdue = itemOf(owner, COMMUNITY, "연체 중", ItemVisibility.PUBLIC);
        Item requested = itemOf(owner, COMMUNITY, "요청 대기 겹침", ItemVisibility.PUBLIC);
        Item afterRange = itemOf(owner, COMMUNITY, "기간 뒤 승인", ItemVisibility.PUBLIC);
        Item free = itemOf(owner, COMMUNITY, "대여 없음", ItemVisibility.PUBLIC);
        em.flush();

        rental(approved, owner, borrower, RentalStatus.APPROVED, LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11), later);
        rental(boundary, owner, borrower, RentalStatus.APPROVED, LocalDate.of(2026, 10, 8), from, later);   // 끝날 = 시작일 (양끝 포함)
        rental(returned, owner, borrower, RentalStatus.RETURNED, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), later);
        rental(overdue, owner, borrower, RentalStatus.ACTIVE, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 30), NOW.minusSeconds(60));
        rental(requested, owner, borrower, RentalStatus.REQUESTED, from, to, later);
        rental(afterRange, owner, borrower, RentalStatus.APPROVED, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14), later);
        em.flush();

        List<String> titles = itemRepository.findAll(ItemSpecifications.allOf(
                        ItemSpecifications.community(COMMUNITY),
                        ItemSpecifications.publicOnly(),
                        ItemSpecifications.availableForRental(from, to, NOW)))
                .stream().map(Item::getTitle).toList();

        assertThat(titles).containsExactlyInAnyOrder(requested.getTitle(), afterRange.getTitle(), free.getTitle());
    }

    @Test
    @DisplayName("기간을 고르지 않으면 대여 점유 조건을 붙이지 않는다")
    void noRangeMeansNoRentalCondition() {
        assertThat(ItemSpecifications.availableForRental(null, null, NOW)).isNull();
    }
}
