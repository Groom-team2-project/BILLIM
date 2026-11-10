package com.billim.domain.chat.service;

import com.billim.MockChatRedisListener;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatRoomRentalReadRepository;
import com.billim.domain.community.service.CommunityMembershipService;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.domain.safety.entity.Report;
import com.billim.domain.safety.entity.ReportAction;
import com.billim.domain.safety.entity.ReportActionType;
import com.billim.domain.safety.entity.ReportReasonCode;
import com.billim.domain.safety.entity.ReportTargetType;
import com.billim.domain.safety.entity.Sanction;
import com.billim.domain.safety.repository.ReportRepository;
import com.billim.domain.safety.repository.ReportActionRepository;
import com.billim.domain.safety.repository.SanctionRepository;
import com.billim.domain.safety.service.BlockService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.profiles.active=perf",
    "spring.security.oauth2.client.registration.kakao.client-id=test",
    "spring.security.oauth2.client.registration.kakao.client-secret=test",
    "billim.notification.outbox.enabled=false"
})
@Import(ChatSanctionRentalPolicyDbTest.MySql.class)
@MockChatRedisListener
@Transactional
class ChatSanctionRentalPolicyDbTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean @ServiceConnection
        MySQLContainer mysql() { return new MySQLContainer("mysql:8.4"); }
    }

    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired ItemRepository items;
    @Autowired RentalRepository rentals;
    @Autowired ReportRepository reports;
    @Autowired ReportActionRepository actions;
    @Autowired SanctionRepository sanctions;
    @Autowired ChatRoomRentalReadRepository eligibility;
    @Autowired ChatRoomOpenService open;
    @Autowired ChatRoomViewService view;
    @Autowired ChatSendAccessPolicy send;
    @MockitoBean CommunityMembershipService memberships;
    @MockitoBean BlockService blocks;
    private Long owner;
    private Long borrower;
    private Long itemId;
    private ChatRoom room;
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        owner = members.saveAndFlush(Member.register("소유자")).getId();
        borrower = members.saveAndFlush(Member.register("요청자")).getId();
        when(memberships.findActiveCommunityId(owner)).thenReturn(Optional.of(100L));
        when(memberships.findActiveCommunityId(borrower)).thenReturn(Optional.of(100L));
        itemId = items.saveAndFlush(Item.create(owner, 100L,
            categories.findByActiveTrueOrderBySortOrderAscIdAsc().getFirst().getId(), 10L,
            "정리 대화 검증", null, LocalDate.now(), LocalDate.now().plusDays(30), now)).getId();
        room = open.open(itemId, borrower);
    }

    @Test
    @DisplayName("제재 전에 승인된 미완료 거래는 양방향 전송·상세·목록에서 같은 예외 적용")
    void preSanctionApprovalAllowsBothDirections() {
        Rental eligible = rental(now.minusSeconds(120));
        sanction(owner, now.minusSeconds(60), ReportActionType.BAN);
        for (Long member : List.of(owner, borrower)) {
            assertCode(() -> send.requireCanSend(room, member, null));
            assertThatCode(() -> send.requireCanSend(room, member, eligible.getId())).doesNotThrowAnyException();
            var detail = view.get(room.getId(), member).access();
            var listed = view.list(member, "ALL", 0, 20).items().getFirst().access();
            assertThat(detail).isEqualTo(listed);
            assertThat(detail.canSend()).isTrue();
            assertThat(detail.reason()).isEqualTo("EXISTING_RENTAL_ONLY");
            assertThat(detail.allowedRentalIds()).containsExactly(eligible.getId().toString());
        }
    }

    @Test
    @DisplayName("미승인·제재와 동시 승인·제재 후 승인 거래는 예외 불가")
    void approvalBoundaryAndRequestedRentalAreRejected() {
        Instant start = now.minusSeconds(60);
        Rental before = rental(start.minusSeconds(1));
        Rental same = rental(start);
        Rental after = rental(start.plusSeconds(1));
        Rental requested = rental(null);
        sanction(borrower, start, ReportActionType.BAN);
        assertThat(allowed(List.of(before, same, after, requested))).containsExactly(before.getId());
        for (Long member : List.of(owner, borrower)) {
            for (Rental denied : List.of(same, after, requested)) {
                assertCode(() -> send.requireCanSend(room, member, denied.getId()));
            }
        }
    }

    @Test
    @DisplayName("양측 다중 제재 중 가장 이른 유효 제재보다 먼저 승인돼야 하며 해제·만료는 제외")
    void allEffectiveSanctionsMustFollowApproval() {
        Rental rental = rental(now.minusSeconds(100));
        sanction(owner, now.minusSeconds(50), ReportActionType.BAN);
        Sanction earlier = sanction(borrower, now.minusSeconds(150), ReportActionType.BAN);
        assertThat(allowed(List.of(rental))).isEmpty();
        earlier.revoke(owner, "해제 검증", now);
        sanctions.saveAndFlush(earlier);
        sanction(borrower, now.minusSeconds(86400 * 10), ReportActionType.SUSPEND_7D);
        assertThat(allowed(List.of(rental))).containsExactly(rental.getId());
        assertThatCode(() -> send.requireCanSend(room, owner, rental.getId())).doesNotThrowAnyException();
        assertThatCode(() -> send.requireCanSend(room, borrower, rental.getId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정지 전 승인돼도 완료·취소 거래는 예외 종료, ACTIVE는 계속 허용")
    void onlyUnfinishedRentalsRemainEligible() {
        Rental approved = rental(now.minusSeconds(120));
        Rental active = rental(now.minusSeconds(120));
        active.handover(now.minusSeconds(90));
        rentals.saveAndFlush(active);
        Rental canceled = rental(now.minusSeconds(120));
        canceled.cancel("취소");
        rentals.saveAndFlush(canceled);
        sanction(owner, now.minusSeconds(60), ReportActionType.BAN);
        assertThat(allowed(List.of(approved, active, canceled)))
            .containsExactlyInAnyOrder(approved.getId(), active.getId());
        active.returnItem(now.minusSeconds(30), null);
        rentals.saveAndFlush(active);
        assertThat(allowed(List.of(active, canceled))).isEmpty();
    }

    @Test
    @DisplayName("정지 전 승인 거래가 없으면 양측 상세·목록 전송 금지, 해제 후 일반 대화 복구")
    void noEligibleRentalBlocksBothSidesUntilRevoked() {
        rental(null);
        Sanction sanction = sanction(owner, now.minusSeconds(60), ReportActionType.BAN);
        for (Long member : List.of(owner, borrower)) {
            var detail = view.get(room.getId(), member).access();
            var listed = view.list(member, "ALL", 0, 20).items().getFirst().access();
            assertThat(detail).isEqualTo(listed);
            assertThat(detail.canSend()).isFalse();
            assertThat(detail.reason()).isEqualTo("RESTRICTED");
            assertThat(detail.allowedRentalIds()).isEmpty();
        }
        sanction.revoke(borrower, "해제 검증", now);
        sanctions.saveAndFlush(sanction);
        for (Long member : List.of(owner, borrower)) {
            assertThatCode(() -> send.requireCanSend(room, member, null)).doesNotThrowAnyException();
            assertThat(view.get(room.getId(), member).access().reason()).isEqualTo("NORMAL");
        }
    }

    private Rental rental(Instant approvedAt) {
        Rental rental = Rental.request(itemId, owner, borrower, 100L,
            LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Seoul")), LocalDate.now().plusDays(2), now.plusSeconds(3600),
            now.plusSeconds(86400 * 3), "정리 대화 검증", 10L, "정문");
        rental.linkChatRoom(room.getId());
        if (approvedAt != null) rental.approve(approvedAt);
        return rentals.saveAndFlush(rental);
    }

    private Sanction sanction(Long target, Instant start, ReportActionType type) {
        Long reporter = target.equals(owner) ? borrower : owner;
        Report report = reports.saveAndFlush(Report.open(reporter, ReportTargetType.MEMBER,
            target, null, 100L, ReportReasonCode.NO_SHOW, null, "{}"));
        ReportAction action = actions.saveAndFlush(ReportAction.of(report.getId(), reporter, type, "검증 조치", null));
        return sanctions.saveAndFlush(Sanction.from(target, action.getId(), type, start));
    }

    private List<Long> allowed(List<Rental> candidates) {
        return eligibility.findAllowedRentalIds(candidates.stream().map(Rental::getId).toList(),
            List.of(RentalStatus.APPROVED, RentalStatus.ACTIVE), now);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_RESTRICTED));
    }
}
