package com.billim.domain.item.service;

import com.billim.domain.item.dto.MediaFileResponse;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.MediaFile;
import com.billim.domain.item.entity.MediaStatus;
import com.billim.domain.item.repository.ItemImageRepository;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.item.repository.MediaFileRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.IntConsumer;

/** 사진 업로드·읽기·미연결 삭제 (API 명세 B_014~B_016) */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    public record MediaContent(byte[] bytes, String mimeType) {
    }

    private final MediaFileRepository mediaFileRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemRepository itemRepository;
    private final ImageInspector inspector;
    private final MediaStorage storage;
    private final ItemAccessPolicy accessPolicy;
    private final Clock clock;

    public MediaService(MediaFileRepository mediaFileRepository, ItemImageRepository itemImageRepository,
                        ItemRepository itemRepository, ImageInspector inspector, MediaStorage storage,
                        ItemAccessPolicy accessPolicy, Clock clock) {
        this.mediaFileRepository = mediaFileRepository;
        this.itemImageRepository = itemImageRepository;
        this.itemRepository = itemRepository;
        this.inspector = inspector;
        this.storage = storage;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
    }

    /** 검사·재인코딩 → 파일 원자 저장 → 메타데이터 기록. 트랜잭션이 커밋되지 않으면 방금 쓴 파일을 지운다. */
    @Transactional
    public MediaFileResponse upload(long memberId, byte[] data, String declaredMime) {
        Instant now = clock.instant();
        ImageInspector.Result r = inspector.inspect(data, declaredMime);
        String key = "%04d/%02d/%s.%s".formatted(
                now.atZone(ZoneOffset.UTC).getYear(), now.atZone(ZoneOffset.UTC).getMonthValue(),
                UUID.randomUUID(), r.extension());
        try {
            storage.save(key, r.bytes());
        } catch (IOException e) {
            log.error("사진 저장 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        // 이후 커밋이 실패해도 방금 쓴 파일이 고아로 남지 않게 롤백 시 지운다.
        afterCompletion(status -> {
            if (status != TransactionSynchronization.STATUS_COMMITTED) {
                deleteQuietly(key);
            }
        });
        try {
            MediaFile saved = mediaFileRepository.saveAndFlush(
                    MediaFile.temp(memberId, key, r.mimeType(), r.bytes().length, r.width(), r.height(), now));
            return MediaFileResponse.from(saved);
        } catch (RuntimeException e) {
            deleteQuietly(key);   // 메타데이터 기록 실패 시 즉시 정리
            throw e;
        }
    }

    /** TEMP는 업로더만. ATTACHED는 물건 조회 권한이 있는 회원만. 그 외는 존재를 숨기는 404 */
    @Transactional(readOnly = true)
    public MediaContent read(long memberId, long mediaId) {
        MediaFile m = mediaFileRepository.findById(mediaId)
                .filter(f -> f.getStatus() != MediaStatus.DELETED)
                .orElseThrow(MediaService::notFound);
        if (m.getStatus() == MediaStatus.TEMP) {
            if (!m.getUploaderId().equals(memberId)) {
                throw notFound();
            }
        } else {
            Long itemId = itemImageRepository.findItemIdByMediaFileId(mediaId).orElseThrow(MediaService::notFound);
            Item item = itemRepository.findById(itemId).orElseThrow(MediaService::notFound);
            // TODO(C): 물건을 볼 수 없게 된 뒤에도 기존 대여 당사자(요청자·소유자)는 사진을 읽을 수 있어야 한다.
            if (!accessPolicy.canView(item, memberId)) {
                throw notFound();
            }
        }
        try {
            return new MediaContent(storage.read(m.getStorageKey()), m.getMimeType());
        } catch (IOException e) {
            log.error("사진 파일 읽기 실패 mediaId={}", mediaId, e);
            throw notFound();
        }
    }

    /** 업로더 자신의 TEMP만. ATTACHED는 409 MEDIA_IN_USE. 파일 삭제 실패는 DB 상태(DELETED)로 남겨 정리 때 재시도 */
    @Transactional
    public void deleteTemp(long memberId, long mediaId) {
        Instant now = clock.instant();
        MediaFile m = mediaFileRepository.findByIdForUpdate(mediaId)
                .filter(f -> f.getStatus() != MediaStatus.DELETED && f.getUploaderId().equals(memberId))
                .orElseThrow(MediaService::notFound);
        m.deleteTemp(now);
        String key = m.getStorageKey();
        // DB 상태(DELETED)가 확정된 뒤에만 파일을 지운다. 실패하면 정리 작업이 재시도
        afterCompletion(status -> {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                deleteQuietly(key);
            }
        });
    }

    /** 트랜잭션이 없으면(단위 테스트 등) 즉시 커밋된 것으로 보고 실행한다. */
    private static void afterCompletion(IntConsumer action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    action.accept(status);
                }
            });
        } else {
            action.accept(TransactionSynchronization.STATUS_COMMITTED);
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException e) {
            log.warn("사진 파일 삭제 실패, 정리 작업에서 재시도 필요 key={}", key, e);
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
