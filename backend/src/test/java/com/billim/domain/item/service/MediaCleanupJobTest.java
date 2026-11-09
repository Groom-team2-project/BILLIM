package com.billim.domain.item.service;

import com.billim.domain.item.repository.ItemImageRepository;
import com.billim.domain.item.repository.MediaFileRepository;
import com.billim.domain.item.repository.MediaFileRepository.StoredMedia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 버려진 사진 정리. 저장소·파일 저장소는 Mock
@ExtendWith(MockitoExtension.class)
class MediaCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-11-04T19:30:00Z");

    @Mock MediaFileRepository mediaFiles;
    @Mock ItemImageRepository itemImages;
    @Mock MediaStorage storage;
    @Mock PlatformTransactionManager transactions;

    MediaCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new MediaCleanupJob(mediaFiles, itemImages, storage, Clock.fixed(NOW, ZoneOffset.UTC), transactions,
                Duration.ofDays(30), 2);
        lenient().when(mediaFiles.findExpiredTemp(any(), anyLong(), any())).thenReturn(List.of());
        lenient().when(mediaFiles.findDeletedBefore(any(), anyLong(), any())).thenReturn(List.of());
        lenient().when(mediaFiles.findOfItemsDeletedBefore(any(), anyLong(), any())).thenReturn(List.of());
    }

    private static StoredMedia stored(long id, String key) {
        return new StoredMedia() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getStorageKey() {
                return key;
            }
        };
    }

    @Test
    @DisplayName("만료 임시 사진은 원본·썸네일 파일을 먼저 지우고, 물건 연결과 사진 행을 지운다")
    void purgesExpiredTempFilesThenRows() throws IOException {
        when(mediaFiles.findExpiredTemp(eq(NOW), eq(0L), any())).thenReturn(List.of(stored(1, "a.jpg")));

        job.purge();

        InOrder order = inOrder(storage, itemImages, mediaFiles);
        order.verify(storage).delete("a.jpg");
        order.verify(storage).delete("a.jpg.thumb.jpg");
        order.verify(itemImages).deleteByMediaFileIdIn(List.of(1L));
        order.verify(mediaFiles).deleteAllByIdIn(List.of(1L));
    }

    @Test
    @DisplayName("삭제 표시된 사진과 삭제한 물건의 사진은 지금으로부터 30일 전을 기준으로 고른다")
    void deletedMediaUseRetentionCutoff() {
        job.purge();

        ArgumentCaptor<Instant> removed = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> ofItems = ArgumentCaptor.forClass(Instant.class);
        verify(mediaFiles).findDeletedBefore(removed.capture(), eq(0L), any());
        verify(mediaFiles).findOfItemsDeletedBefore(ofItems.capture(), eq(0L), any());
        assertThat(removed.getValue()).isEqualTo(NOW.minus(Duration.ofDays(30)));
        assertThat(ofItems.getValue()).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("파일 삭제에 실패한 사진은 행을 남겨 다음 정리에서 다시 시도한다")
    void keepsRowWhenFileDeleteFails() throws IOException {
        when(mediaFiles.findDeletedBefore(any(), eq(0L), any())).thenReturn(List.of(stored(1, "a.jpg")));
        lenient().doThrow(new IOException("디스크 오류")).when(storage).delete("a.jpg");
        when(mediaFiles.findOfItemsDeletedBefore(any(), eq(0L), any()))
                .thenReturn(List.of(stored(2, "b.jpg"), stored(3, "c.jpg")));
        lenient().doThrow(new IOException("디스크 오류")).when(storage).delete("c.jpg");

        job.purge();

        verify(mediaFiles, never()).deleteAllByIdIn(List.of(1L));
        verify(mediaFiles).deleteAllByIdIn(List.of(2L));
        verify(itemImages).deleteByMediaFileIdIn(List.of(2L));
    }

    @Test
    @DisplayName("묶음이 가득 차면 마지막 사진 다음부터 이어서 조회한다")
    void continuesAfterLastIdWhenChunkIsFull() {
        when(mediaFiles.findExpiredTemp(eq(NOW), eq(0L), any()))
                .thenReturn(List.of(stored(1, "a.jpg"), stored(2, "b.jpg")));
        when(mediaFiles.findExpiredTemp(eq(NOW), eq(2L), any())).thenReturn(List.of(stored(5, "e.jpg")));

        job.purge();

        verify(mediaFiles).deleteAllByIdIn(List.of(1L, 2L));
        verify(mediaFiles).deleteAllByIdIn(List.of(5L));
        verify(mediaFiles, never()).findExpiredTemp(eq(NOW), eq(5L), any());
    }

    @Test
    @DisplayName("묶음 크기가 0 이하면 기동 시점에 거부한다")
    void rejectsNonPositiveChunk() {
        assertThatThrownBy(() -> new MediaCleanupJob(mediaFiles, itemImages, storage, Clock.systemUTC(),
                transactions, Duration.ofDays(30), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
