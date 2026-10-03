package com.billim.domain.item.repository;

import com.billim.domain.item.entity.Category;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemImage;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.entity.MediaFile;
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
import org.springframework.data.domain.Sort;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
}
