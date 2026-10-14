package com.billim.domain.safety.entity;

import com.billim.global.entity.BaseTimeEntity;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * notices — 커뮤니티 공지.
 * 본문은 일반 텍스트로 보관하고 렌더링은 화면이 한다. 삭제는 논리 삭제다. (ERD 4.7)
 */
@Entity
@Table(
        name = "notices",
        indexes = @Index(name = "idx_notices_community_deleted_pinned",
                columnList = "community_id,deleted_at,pinned,created_at,id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {

    public static final int TITLE_MAX = 150;
    public static final int BODY_MAX = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false, updatable = false)
    private Long communityId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(nullable = false, length = TITLE_MAX)
    private String title;

    // ERD는 text. @Lob은 MySQL CLOB(tinytext)로 매핑돼 스키마 검증에서 어긋난다
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static Notice of(long communityId, long authorId, String title, String body, boolean pinned) {
        Notice notice = new Notice();
        notice.communityId = communityId;
        notice.authorId = authorId;
        notice.title = requireText(title, TITLE_MAX, "제목");
        notice.body = requireText(body, BODY_MAX, "본문");
        notice.pinned = pinned;
        return notice;
    }

    /** 전체 필드 교체 (API 명세 E_067) */
    public void update(String title, String body, boolean pinned) {
        requireNotDeleted();
        this.title = requireText(title, TITLE_MAX, "제목");
        this.body = requireText(body, BODY_MAX, "본문");
        this.pinned = pinned;
    }

    /** 논리 삭제. 목록·상세에서 빠진다 */
    public void delete(Instant now) {
        requireNotDeleted();
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean belongsTo(long communityId) {
        return this.communityId == communityId;
    }

    private void requireNotDeleted() {
        if (isDeleted()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private static String requireText(String value, int max, String label) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty() || trimmed.length() > max) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "%s은 1~%d자로 입력해 주세요.".formatted(label, max));
        }
        return trimmed;
    }
}
