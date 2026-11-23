package com.billim.domain.rental.service;

import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.service.*;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.entity.RentalStatusHistory;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.domain.rental.repository.RentalStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

/** 요청별 트랜잭션과 최신 상태 재검사로 승인·취소·다중 실행 간 중복 만료 방지 */
@Component
public class RentalExpirationJob {
    private static final Logger log = LoggerFactory.getLogger(RentalExpirationJob.class);
    private final RentalRepository rentals;
    private final RentalStatusHistoryRepository histories;
    private final MemberRepository members;
    private final ItemRepository items;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RentalExpirationJob(RentalRepository rentals, RentalStatusHistoryRepository histories,
            MemberRepository members, ItemRepository items, DomainEventPublisher events,
            Clock clock, PlatformTransactionManager transactions) {
        this.rentals = rentals;
        this.histories = histories;
        this.members = members;
        this.items = items;
        this.events = events;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Scheduled(fixedDelayString = "${billim.rental.expiration-delay-ms:60000}")
    public void expireRequests() {
        var ids = rentals.findExpiredRequestIds(clock.instant(), PageRequest.of(0, 100));
        for (Long id : ids) {
            try {
                transaction.executeWithoutResult(status -> expire(id));
            } catch (RuntimeException e) {
                // 해당 요청은 다음 실행에서 재시도, 나머지 요청은 계속 처리
                log.error("대여 요청 만료 실패: rentalId={}", id, e);
            }
        }
    }

    private void expire(Long id) {
        var participants = rentals.findParticipants(id).orElse(null);
        if (participants == null) return;
        // 사용자 명령과 동일한 회원 ID순 → 물건 → 거래 잠금
        Stream.of(participants.getOwnerId(), participants.getBorrowerId()).distinct().sorted()
                .forEach(memberId -> members.findByIdForUpdate(memberId).orElseThrow());
        items.findByIdForUpdate(participants.getItemId()).orElseThrow();
        var rental = rentals.findByIdForUpdate(id).orElse(null);
        if (rental == null || !rental.expire(clock.instant())) return;
        rentals.flush();
        histories.saveAndFlush(RentalStatusHistory.changed(rental, RentalStatus.REQUESTED, null));
        events.publish(DomainEvent.of(EventType.RENTAL_EXPIRED, id, rental.getVersion(),
                EventPayload.forRental(id, List.of(
                        EventPayload.Recipient.of(rental.getOwnerId(), RecipientRole.OWNER),
                        EventPayload.Recipient.of(rental.getBorrowerId(), RecipientRole.BORROWER)),
                        EventPayload.Display.ofRental(null, rental.getItemTitleSnapshot(),
                                rental.getStartDate(), rental.getEndDate()))));
    }
}
