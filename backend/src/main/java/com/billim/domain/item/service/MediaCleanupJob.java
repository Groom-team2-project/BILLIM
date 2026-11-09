package com.billim.domain.item.service;

import com.billim.domain.item.repository.ItemImageRepository;
import com.billim.domain.item.repository.MediaFileRepository;
import com.billim.domain.item.repository.MediaFileRepository.StoredMedia;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 버려진 사진 정리. 원본과 썸네일 파일을 지운 뒤 사진 행(과 물건 연결)을 지운다.
 *
 * <ul>
 *   <li>임시 사진: 물건에 연결되지 않은 채 만료(24시간)되면 바로</li>
 *   <li>수정에서 빠진 사진: 삭제 표시 후 보관 기간(기본 30일)이 지나면</li>
 *   <li>삭제한 물건의 사진: 물건 삭제 후 보관 기간이 지나면. 그 전까지는 거래 확인·신고 근거로 보관</li>
 * </ul>
 *
 * <p>파일을 먼저 지우고, 지운 것만 행을 지운다. 파일 삭제가 실패하면 행이 남아 다음 실행에서 다시 시도한다.
 * 묶음 하나가 트랜잭션 하나라 정리 중에도 사진 테이블 잠금이 길게 유지되지 않는다.
 *
 * <p>다중 인스턴스에서는 한 인스턴스만 실행 (shedlock). 파일 삭제는 같은 공유 디스크를 대상으로 한다.
 */
@Component
public class MediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

    /** 묶음 반복 한도 — 한 주기가 무한정 돌지 않게 하는 상한 */
    private static final int MAX_ROUNDS = 1_000;

    private final MediaFileRepository mediaFiles;
    private final ItemImageRepository itemImages;
    private final MediaStorage storage;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final Duration retention;
    private final int chunkSize;

    public MediaCleanupJob(MediaFileRepository mediaFiles, ItemImageRepository itemImages, MediaStorage storage,
                           Clock clock, PlatformTransactionManager transactions,
                           @Value("${billim.media.cleanup-retention:30d}") Duration retention,
                           @Value("${billim.media.cleanup-chunk-size:200}") int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("billim.media.cleanup-chunk-size는 양수 필요: " + chunkSize);
        }
        this.mediaFiles = mediaFiles;
        this.itemImages = itemImages;
        this.storage = storage;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
        this.retention = retention;
        this.chunkSize = chunkSize;
    }

    @Scheduled(cron = "${billim.media.cleanup-cron:0 30 4 * * *}")
    @SchedulerLock(name = "media-cleanup", lockAtLeastFor = "1m", lockAtMostFor = "30m")
    public void purge() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(retention);
        int temps = purgeInChunks((afterId, page) -> mediaFiles.findExpiredTemp(now, afterId, page));
        int removed = purgeInChunks((afterId, page) -> mediaFiles.findDeletedBefore(cutoff, afterId, page));
        int ofDeletedItems = purgeInChunks((afterId, page) -> mediaFiles.findOfItemsDeletedBefore(cutoff, afterId, page));
        if (temps > 0 || removed > 0 || ofDeletedItems > 0) {
            log.info("사진 정리 — 만료 임시 {}건, 삭제 표시 {}건, 삭제한 물건 {}건", temps, removed, ofDeletedItems);
        }
    }

    @FunctionalInterface
    interface ChunkFinder {
        List<StoredMedia> find(long afterId, Pageable page);
    }

    // id 커서로 진행해서, 파일 삭제가 계속 실패하는 사진이 있어도 같은 묶음을 반복하지 않는다
    int purgeInChunks(ChunkFinder finder) {
        int purged = 0;
        long afterId = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<StoredMedia> chunk = finder.find(afterId, PageRequest.of(0, chunkSize));
            if (chunk.isEmpty()) {
                break;
            }
            List<Long> filesGone = new ArrayList<>();
            for (StoredMedia media : chunk) {
                if (deleteFiles(media.getStorageKey())) {
                    filesGone.add(media.getId());
                }
            }
            if (!filesGone.isEmpty()) {
                transaction.executeWithoutResult(status -> {
                    itemImages.deleteByMediaFileIdIn(filesGone);
                    mediaFiles.deleteAllByIdIn(filesGone);
                });
                purged += filesGone.size();
            }
            afterId = chunk.getLast().getId();
            if (chunk.size() < chunkSize) {
                break;
            }
        }
        return purged;
    }

    // 원본과 썸네일. 없는 파일은 저장소가 무시한다
    private boolean deleteFiles(String storageKey) {
        try {
            storage.delete(storageKey);
            storage.delete(MediaService.thumbnailKey(storageKey));
            return true;
        } catch (IOException e) {
            log.warn("사진 파일 삭제 실패, 다음 정리에서 재시도 key={}", storageKey, e);
            return false;
        }
    }
}
