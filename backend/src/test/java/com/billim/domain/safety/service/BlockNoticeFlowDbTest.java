package com.billim.domain.safety.service;

import com.billim.MockChatRedisListener;

import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityMembership;
import com.billim.domain.community.entity.CommunityStatus;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import com.billim.domain.community.repository.CommunityRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.safety.dto.CreateNoticeRequest;
import com.billim.domain.safety.dto.NoticeResponse;
import com.billim.domain.safety.dto.UpdateNoticeRequest;
import com.billim.domain.safety.repository.BlockRepository;
import com.billim.domain.safety.repository.NoticeRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 차단과 공지의 통합 동작 (API 명세 12.1 ~ 12.3, 14.1 ~ 14.6).
 * 커밋 후 상태를 확인해야 하므로 테스트를 트랜잭션으로 감싸지 않고 직접 정리한다.
 */
@Import(BlockNoticeFlowDbTest.MySql.class)
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
        "billim.notification.outbox.enabled=false"
})
@MockChatRedisListener
class BlockNoticeFlowDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    @Autowired BlockService blockService;
    @Autowired NoticeService noticeService;
    @Autowired BlockRepository blockRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired CommunityRepository communityRepository;
    @Autowired CommunityMembershipRepository membershipRepository;
    @Autowired JdbcTemplate jdbc;

    private long me;
    private long neighbor;
    private long stranger;
    private long admin;
    private long homeId;
    private long otherCommunityId;

    @BeforeEach
    void setUp() {
        clean();
        homeId = saveCommunity("새솔마을 5단지", CommunityStatus.ACTIVE);
        otherCommunityId = saveCommunity("배곧한울마을", CommunityStatus.ACTIVE);

        me = saveMember("소빈", MemberRole.USER);
        neighbor = saveMember("정우", MemberRole.USER);
        stranger = saveMember("지민", MemberRole.USER);
        admin = saveMember("운영자", MemberRole.ADMIN);

        joinCommunity(me, homeId);
        joinCommunity(neighbor, homeId);
        joinCommunity(stranger, otherCommunityId);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    /** 자식 → 부모 순서로 지운다 */
    private void clean() {
        jdbc.update("DELETE FROM notices");
        jdbc.update("DELETE FROM blocks");
        jdbc.update("UPDATE members SET active_community_id = NULL");
        jdbc.update("DELETE FROM community_memberships");
        jdbc.update("DELETE FROM communities");
        jdbc.update("DELETE FROM members");
    }

    // ---------- 차단 ----------

    @Test
    @DisplayName("같은 동네 이웃을 차단하면 목록에 남고 양방향으로 판정된다")
    void blockNeighbor() {
        blockService.block(me, neighbor);

        assertThat(blockService.findMyBlocks(me, 0, 20).items())
                .singleElement()
                .satisfies(block -> assertThat(block.member().displayName()).isEqualTo("정우"));

        // 판정은 양방향 OR이라 상대 쪽에서도 막힌다
        assertThat(blockService.isBlockedBetween(me, neighbor)).isTrue();
        assertThat(blockService.isBlockedBetween(neighbor, me)).isTrue();
    }

    @Test
    @DisplayName("차단 목록은 내가 차단한 방향만 보여준다")
    void listShowsOnlyMyDirection() {
        blockService.block(neighbor, me);

        assertThat(blockService.findMyBlocks(me, 0, 20).items()).isEmpty();
        assertThat(blockService.findMyBlocks(neighbor, 0, 20).items()).hasSize(1);
        // 그래도 제한은 양방향이다
        assertThat(blockService.isBlockedBetween(me, neighbor)).isTrue();
    }

    @Test
    @DisplayName("이미 차단한 이웃을 다시 차단해도 성공하고 행이 늘지 않는다")
    void repeatedBlockIsSafe() {
        blockService.block(me, neighbor);
        blockService.block(me, neighbor);

        assertThat(blockRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("자기 자신은 차단할 수 없다")
    void selfBlockIsRejected() {
        assertThatThrownBy(() -> blockService.block(me, me))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        assertThat(blockService.isBlockedBetween(me, me)).isFalse();
    }

    @Test
    @DisplayName("같은 동네도 아니고 거래한 적도 없는 회원은 차단할 수 없다")
    void unknownMemberCannotBeBlocked() {
        assertThatThrownBy(() -> blockService.block(me, stranger))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        assertThat(blockRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("해제는 내가 만든 방향만 지운다. 상대 차단은 남아 제한이 계속된다")
    void unblockRemovesOnlyMyDirection() {
        blockService.block(me, neighbor);
        blockService.block(neighbor, me);

        blockService.unblock(me, neighbor);

        assertThat(blockService.findMyBlocks(me, 0, 20).items()).isEmpty();
        assertThat(blockService.findMyBlocks(neighbor, 0, 20).items()).hasSize(1);
        assertThat(blockService.isBlockedBetween(me, neighbor)).isTrue();
    }

    @Test
    @DisplayName("없는 차단 관계를 해제해도 성공한다")
    void unblockWithoutRelationIsSafe() {
        blockService.unblock(me, neighbor);

        assertThat(blockRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("차단 관계인 회원만 한 번에 걸러낸다")
    void findBlockedAmongFiltersCandidates() {
        blockService.block(me, neighbor);

        Set<Long> blocked = blockService.findBlockedAmong(me, Set.of(neighbor, stranger, admin));

        assertThat(blocked).containsExactly(neighbor);
        assertThat(blockService.findBlockedAmong(me, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("차단 관계 회원 전체를 양방향으로 돌려준다")
    void findBlockRelatedMemberIdsIsBidirectional() {
        blockService.block(me, neighbor);

        assertThat(blockService.findBlockRelatedMemberIds(me)).containsExactly(neighbor);
        assertThat(blockService.findBlockRelatedMemberIds(neighbor)).containsExactly(me);
        assertThat(blockService.findBlockRelatedMemberIds(stranger)).isEmpty();
    }

    @Test
    @DisplayName("내가 차단한 경우만 단방향 판정이 참이다")
    void hasBlockedIsOneDirectional() {
        blockService.block(me, neighbor);

        assertThat(blockService.hasBlocked(me, neighbor)).isTrue();
        // 상대 입장에서는 자기가 차단한 게 아니다. 프로필 버튼이 '차단 해제'로 보이면 안 된다
        assertThat(blockService.hasBlocked(neighbor, me)).isFalse();
        // 접근 제한은 양방향이라 둘 다 참이다
        assertThat(blockService.isBlockedBetween(neighbor, me)).isTrue();
    }

    @Test
    @DisplayName("해제하면 내 단방향 판정만 거짓이 되고 상대 차단은 남는다")
    void hasBlockedFollowsOnlyMyDirection() {
        blockService.block(me, neighbor);
        blockService.block(neighbor, me);

        blockService.unblock(me, neighbor);

        assertThat(blockService.hasBlocked(me, neighbor)).isFalse();
        assertThat(blockService.hasBlocked(neighbor, me)).isTrue();
        assertThat(blockService.isBlockedBetween(me, neighbor)).isTrue();
    }

    @Test
    @DisplayName("차단 관계가 없거나 자기 자신이면 단방향 판정은 거짓이다")
    void hasBlockedIsFalseWithoutRelation() {
        assertThat(blockService.hasBlocked(me, neighbor)).isFalse();
        assertThat(blockService.hasBlocked(me, me)).isFalse();
    }

    // ---------- 공지 ----------

    @Test
    @DisplayName("공지를 쓰면 유효 소속 회원이 볼 수 있다")
    void noticeIsVisibleToMembers() {
        NoticeResponse created = createNotice(homeId, "정전 안내", false);

        assertThat(noticeService.findCommunityNotices(me, false, homeId, 0, 20).items())
                .singleElement()
                .satisfies(notice -> assertThat(notice.title()).isEqualTo("정전 안내"));
        assertThat(noticeService.findNotice(me, false, Long.valueOf(created.id())).body())
                .isEqualTo("본문");
    }

    @Test
    @DisplayName("고정 공지가 먼저 나온다")
    void pinnedNoticeComesFirst() {
        createNotice(homeId, "일반 공지", false);
        createNotice(homeId, "고정 공지", true);

        assertThat(noticeService.findCommunityNotices(me, false, homeId, 0, 20).items())
                .extracting("title").containsExactly("고정 공지", "일반 공지");
    }

    @Test
    @DisplayName("다른 동네 회원은 공지 목록을 볼 수 없다")
    void nonMemberCannotSeeNotices() {
        createNotice(homeId, "정전 안내", false);

        assertThatThrownBy(() -> noticeService.findCommunityNotices(stranger, false, homeId, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED));
    }

    @Test
    @DisplayName("다른 동네 공지 상세는 존재를 알리지 않고 404다")
    void otherCommunityNoticeDetailIsNotFound() {
        NoticeResponse created = createNotice(homeId, "정전 안내", false);

        assertThatThrownBy(() ->
                noticeService.findNotice(stranger, false, Long.valueOf(created.id())))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("관리자는 소속과 무관하게 공지를 볼 수 있다")
    void adminSeesNoticesWithoutMembership() {
        NoticeResponse created = createNotice(homeId, "정전 안내", false);

        assertThat(noticeService.findCommunityNotices(admin, true, homeId, 0, 20).items()).hasSize(1);
        assertThat(noticeService.findNotice(admin, true, Long.valueOf(created.id()))).isNotNull();
    }

    @Test
    @DisplayName("관리자 목록은 동네 필터로 걸러진다")
    void adminListFiltersByCommunity() {
        createNotice(homeId, "정전 안내", false);
        createNotice(otherCommunityId, "단수 안내", false);

        assertThat(noticeService.findAllNotices(null, 0, 20).items()).hasSize(2);
        assertThat(noticeService.findAllNotices(homeId, 0, 20).items()).hasSize(1);
    }

    @Test
    @DisplayName("비활성 커뮤니티에는 공지를 쓸 수 없다")
    void inactiveCommunityRejectsNotice() {
        long inactive = saveCommunity("폐지된마을", CommunityStatus.INACTIVE);

        assertThatThrownBy(() -> createNotice(inactive, "안내", false))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("수정하면 전체 필드가 교체되고 version이 오른다")
    void updateReplacesFields() {
        NoticeResponse created = createNotice(homeId, "정전 안내", false);

        NoticeResponse updated = noticeService.update(Long.valueOf(created.id()),
                new UpdateNoticeRequest(created.version(), "정전 안내(수정)", "새 본문", true));

        assertThat(updated.title()).isEqualTo("정전 안내(수정)");
        assertThat(updated.body()).isEqualTo("새 본문");
        assertThat(updated.pinned()).isTrue();
        assertThat(updated.version()).isGreaterThan(created.version());
    }

    @Test
    @DisplayName("오래된 version으로 수정하면 현재 version과 함께 충돌을 알린다")
    void staleVersionUpdateIsRejected() {
        NoticeResponse created = createNotice(homeId, "정전 안내", false);

        assertThatThrownBy(() -> noticeService.update(Long.valueOf(created.id()),
                new UpdateNoticeRequest(created.version() + 5, "제목", "본문", false)))
                .isInstanceOfSatisfying(VersionConflictException.class,
                        e -> assertThat(e.getCurrentVersion()).isEqualTo(created.version()));
    }

    @Test
    @DisplayName("삭제하면 목록·상세에서 빠지고 행은 남는다")
    void deleteIsLogical() {
        NoticeResponse created = createNotice(homeId, "정전 안내", false);
        long noticeId = Long.valueOf(created.id());

        noticeService.delete(noticeId, created.version());

        assertThat(noticeService.findCommunityNotices(me, false, homeId, 0, 20).items()).isEmpty();
        assertThatThrownBy(() -> noticeService.findNotice(me, false, noticeId))
                .isInstanceOf(BusinessException.class);
        assertThat(noticeRepository.findById(noticeId)).get()
                .satisfies(notice -> assertThat(notice.isDeleted()).isTrue());
    }

    @Test
    @DisplayName("삭제된 공지는 다시 수정·삭제할 수 없다")
    void deletedNoticeCannotBeChanged() {
        NoticeResponse created = createNotice(homeId, "정전 안내", false);
        long noticeId = Long.valueOf(created.id());
        noticeService.delete(noticeId, created.version());
        long currentVersion = noticeRepository.findById(noticeId).orElseThrow().getVersion();

        assertThatThrownBy(() -> noticeService.delete(noticeId, currentVersion))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> noticeService.update(noticeId,
                new UpdateNoticeRequest(currentVersion, "제목", "본문", false)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("빈 제목·본문은 거부한다")
    void blankTitleOrBodyIsRejected() {
        assertThatThrownBy(() -> noticeService.create(admin,
                new CreateNoticeRequest(String.valueOf(homeId), "   ", "본문", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    // ---------- fixtures ----------

    private NoticeResponse createNotice(long communityId, String title, boolean pinned) {
        return noticeService.create(admin,
                new CreateNoticeRequest(String.valueOf(communityId), title, "본문", pinned));
    }

    private long saveCommunity(String name, CommunityStatus status) {
        return communityRepository.save(Community.of(
                name, name + " 일대",
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"),
                500, status)).getId();
    }

    private long saveMember(String name, MemberRole role) {
        Member member = memberRepository.save(Member.register(name));
        // role은 도메인 변경 메서드가 없어 테스트에서 직접 세팅한다
        jdbc.update("UPDATE members SET role = ? WHERE id = ?", role.name(), member.getId());
        return member.getId();
    }

    private void joinCommunity(long memberId, long communityId) {
        membershipRepository.save(CommunityMembership.verified(memberId, communityId, Instant.now()));
        jdbc.update("UPDATE members SET active_community_id = ? WHERE id = ?", communityId, memberId);
    }
}
