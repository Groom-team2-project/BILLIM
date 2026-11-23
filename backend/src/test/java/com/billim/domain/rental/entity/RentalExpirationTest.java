package com.billim.domain.rental.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class RentalExpirationTest {
    private static final Instant DEADLINE = Instant.parse("2026-10-10T15:00:00Z");

    private Rental request() {
        return Rental.request(1L, 2L, 3L, 1L, LocalDate.parse("2026-10-10"),
                LocalDate.parse("2026-10-11"), DEADLINE, DEADLINE.plusSeconds(86400), "드릴", 1L, "공용 장소");
    }

    @Test @DisplayName("KST 시작일 다음 날 자정 직전에는 유지, 경계부터 한 번만 만료")
    void expiresAtDeadlineOnce() {
        var rental = request();
        assertThat(rental.expire(DEADLINE.minusNanos(1))).isFalse();
        assertThat(rental.getStatus()).isEqualTo(RentalStatus.REQUESTED);
        assertThat(rental.expire(DEADLINE)).isTrue();
        assertThat(rental.getStatus()).isEqualTo(RentalStatus.EXPIRED);
        assertThat(rental.expire(DEADLINE.plusSeconds(60))).isFalse();
    }

    @Test @DisplayName("승인·취소·거절 후에는 마감이 지나도 만료하지 않음")
    void preservesOtherStates() {
        var approved = request();
        approved.approve(DEADLINE.minusSeconds(60));
        var canceled = request();
        canceled.cancel("취소");
        var rejected = request();
        rejected.reject("거절");
        for (var rental : java.util.List.of(approved, canceled, rejected)) {
            assertThat(rental.expire(DEADLINE)).isFalse();
            assertThat(rental.getStatus()).isNotEqualTo(RentalStatus.EXPIRED);
        }
    }
}
