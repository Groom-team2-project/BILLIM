package com.billim.domain.item.entity;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaFileTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    private MediaFile temp() {
        return MediaFile.temp(1L, "2026/10/a.jpg", "image/jpeg", 1000, 10, 10, NOW);
    }

    private static void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("업로드하면 TEMP이고 24시간 뒤 만료된다")
    void uploadIsTempAndExpiresIn24Hours() {
        MediaFile m = temp();
        assertThat(m.getStatus()).isEqualTo(MediaStatus.TEMP);
        assertThat(m.getExpiresAt()).isEqualTo(NOW.plusSeconds(24 * 3600));
    }

    @Test
    @DisplayName("연결하면 ATTACHED가 된다")
    void attachMakesAttached() {
        MediaFile m = temp();
        m.attach(1L, NOW.plusSeconds(60));
        assertThat(m.getStatus()).isEqualTo(MediaStatus.ATTACHED);
    }

    @Test
    @DisplayName("다른 업로더는 연결할 수 없다")
    void otherUploaderCannotAttach() {
        assertCode(() -> temp().attach(2L, NOW), ErrorCode.MEDIA_NOT_ATTACHABLE);
    }

    @Test
    @DisplayName("만료된 TEMP는 연결할 수 없다")
    void expiredTempCannotAttach() {
        assertCode(() -> temp().attach(1L, NOW.plusSeconds(24 * 3600)), ErrorCode.MEDIA_NOT_ATTACHABLE);
    }

    @Test
    @DisplayName("이미 연결된 사진은 다시 연결할 수 없다")
    void attachedCannotAttachAgain() {
        MediaFile m = temp();
        m.attach(1L, NOW);
        assertCode(() -> m.attach(1L, NOW), ErrorCode.MEDIA_NOT_ATTACHABLE);
    }

    @Test
    @DisplayName("연결된 사진은 미연결 삭제를 거부한다")
    void attachedRejectsTempDelete() {
        MediaFile m = temp();
        m.attach(1L, NOW);
        assertCode(() -> m.deleteTemp(NOW), ErrorCode.MEDIA_IN_USE);
    }

    @Test
    @DisplayName("TEMP 삭제는 DELETED와 deletedAt을 기록한다")
    void tempDeleteRecordsDeleted() {
        MediaFile m = temp();
        m.deleteTemp(NOW.plusSeconds(1));
        assertThat(m.getStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(m.getDeletedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    @DisplayName("크기가 범위를 벗어나면 생성할 수 없다")
    void rejectsOutOfRangeSize() {
        assertCode(() -> MediaFile.temp(1L, "k", "image/png", 0, 1, 1, NOW), ErrorCode.INVALID_IMAGE);
        assertCode(() -> MediaFile.temp(1L, "k", "image/png", 10_485_761L, 1, 1, NOW), ErrorCode.INVALID_IMAGE);
    }
}
