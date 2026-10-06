package com.billim.domain.item.entity;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

/** 영속 볼륨 이미지 메타데이터. storage_key는 API에 노출하지 않는다. */
@Entity
@Table(name = "media_files")
public class MediaFile extends TimestampedEntity {

    /** 미연결 TEMP 업로드 만료 시간 */
    public static final Duration TEMP_TTL = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 30)
    private String mimeType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // ERD는 VARCHAR + CHECK. 네이티브 ENUM 매핑 방지
    @Column(nullable = false, length = 10)
    private MediaStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected MediaFile() {
    }

    public static MediaFile temp(long uploaderId, String storageKey, String mimeType,
                                 long byteSize, int width, int height, Instant now) {
        if (byteSize < 1 || byteSize > 10_485_760L || width < 1 || height < 1) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
        MediaFile m = new MediaFile();
        m.uploaderId = uploaderId;
        m.storageKey = storageKey;
        m.mimeType = mimeType;
        m.byteSize = byteSize;
        m.width = width;
        m.height = height;
        m.status = MediaStatus.TEMP;
        m.expiresAt = now.plus(TEMP_TTL);
        m.initTimestamps(now);
        return m;
    }

    /** 요청 회원의 만료되지 않은 TEMP 사진인지 */
    public boolean isAttachableBy(long memberId, Instant now) {
        return status == MediaStatus.TEMP
                && uploaderId.equals(memberId)
                && expiresAt.isAfter(now);
    }

    /** TEMP → ATTACHED. 연결 불가 상태면 MEDIA_NOT_ATTACHABLE */
    public void attach(long memberId, Instant now) {
        if (!isAttachableBy(memberId, now)) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE);
        }
        this.status = MediaStatus.ATTACHED;
        touch(now);
    }

    /** 미연결 TEMP 삭제. 연결된 사진은 MEDIA_IN_USE */
    public void deleteTemp(Instant now) {
        if (status == MediaStatus.ATTACHED) {
            throw new BusinessException(ErrorCode.MEDIA_IN_USE);
        }
        markDeleted(now);
    }

    /** 물건에서 제거된 사진 정리 표시. 파일은 정리 작업이 지운다. */
    public void markDeleted(Instant now) {
        if (status == MediaStatus.DELETED) {
            return;
        }
        this.status = MediaStatus.DELETED;
        this.deletedAt = now;
        touch(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getByteSize() {
        return byteSize;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public MediaStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
