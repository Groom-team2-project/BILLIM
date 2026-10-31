package com.billim.domain.notification.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OutboxDispatcherTest {

    @Test
    void rejectsNonPositiveBatchSizeAtStartup() {
        for (int batchSize : new int[] {0, -1}) {
            assertThatThrownBy(() -> new OutboxDispatcher(
                mock(OutboxEventStore.class), List.of(), Clock.systemUTC(), batchSize,
                Duration.ofMinutes(1), Duration.ofSeconds(10), 5
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch-size");
        }
    }
}
