package com.billim.domain.item.service;

import com.billim.domain.item.dto.CreateItemRequest;
import com.billim.domain.item.dto.MediaFileResponse;
import com.billim.global.entity.IdempotencyRecord;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.json.JsonCodec;
import com.billim.global.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 물건 등록·사진 업로드 재전송 처리 (API 명세 0.3). 저장소는 Mock, JSON 변환은 실제 JsonCodec
@ExtendWith(MockitoExtension.class)
class ItemIdempotencyTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final long ME = 1L;
    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";
    private static final String OP = ItemIdempotency.UPLOAD_MEDIA;

    @Mock IdempotencyRecordRepository records;

    final JsonCodec json = new JsonCodec();
    ItemIdempotency idempotency;

    @BeforeEach
    void setUp() {
        idempotency = new ItemIdempotency(records, json);
    }

    private static MediaFileResponse response() {
        return new MediaFileResponse("5", "image/jpeg", 100, 40, 30, "/api/v1/media/5/content", "TEMP",
                NOW.plus(Duration.ofHours(24)));
    }

    private IdempotencyRecord stored(String hash, Instant expiresAt) {
        return IdempotencyRecord.succeeded(ME, OP, KEY, hash, 201, json.write(response()), expiresAt);
    }

    @Test
    @DisplayName("처음 보는 키면 재사용할 응답이 없다")
    void unknownKeyHasNoReplay() {
        when(records.findByMemberIdAndOperationKeyAndRequestKey(ME, OP, KEY)).thenReturn(Optional.empty());
        assertThat(idempotency.replay(ME, OP, KEY, "h", 201, MediaFileResponse.class, NOW)).isEmpty();
    }

    @Test
    @DisplayName("같은 키·같은 요청이면 저장된 응답을 그대로 돌려준다")
    void sameKeySameRequestReplaysStoredResponse() {
        when(records.findByMemberIdAndOperationKeyAndRequestKey(ME, OP, KEY))
                .thenReturn(Optional.of(stored("h", NOW.plusSeconds(60))));

        Optional<MediaFileResponse> replay = idempotency.replay(ME, OP, KEY, "h", 201, MediaFileResponse.class, NOW);

        assertThat(replay).contains(response());
    }

    @Test
    @DisplayName("같은 키에 다른 요청 내용이면 409 IDEMPOTENCY_KEY_REUSED다")
    void sameKeyDifferentRequestIsConflict() {
        when(records.findByMemberIdAndOperationKeyAndRequestKey(ME, OP, KEY))
                .thenReturn(Optional.of(stored("h", NOW.plusSeconds(60))));

        assertThatThrownBy(() -> idempotency.replay(ME, OP, KEY, "other", 201, MediaFileResponse.class, NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    @DisplayName("24시간이 지난 기록은 지우고 새 요청으로 처리한다")
    void expiredRecordIsDeletedAndIgnored() {
        IdempotencyRecord expired = stored("h", NOW);
        when(records.findByMemberIdAndOperationKeyAndRequestKey(ME, OP, KEY)).thenReturn(Optional.of(expired));

        assertThat(idempotency.replay(ME, OP, KEY, "h", 201, MediaFileResponse.class, NOW)).isEmpty();
        verify(records).delete(expired);
    }

    @Test
    @DisplayName("성공 응답을 요청 해시·상태와 함께 24시간 보관한다")
    void recordKeepsResponseFor24Hours() {
        idempotency.record(ME, OP, KEY, "h", 201, response(), NOW);

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(records).saveAndFlush(captor.capture());
        IdempotencyRecord saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(ME);
        assertThat(saved.getOperationKey()).isEqualTo(OP);
        assertThat(saved.getRequestKey()).isEqualTo(KEY);
        assertThat(saved.getRequestHash()).isEqualTo("h");
        assertThat(saved.getHttpStatus()).isEqualTo(201);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(json.read(saved.getResponseBody(), MediaFileResponse.class)).isEqualTo(response());
    }

    @Test
    @DisplayName("요청 본문 해시는 내용이 같으면 같고, 다르면 다르다")
    void requestHashFollowsContent() {
        LocalDate start = LocalDate.of(2026, 10, 5);
        LocalDate end = LocalDate.of(2026, 10, 10);
        CreateItemRequest a = new CreateItemRequest("전동드릴", null, "1", "10", start, end, List.of("5"));
        CreateItemRequest same = new CreateItemRequest("전동드릴", null, "1", "10", start, end, List.of("5"));
        CreateItemRequest other = new CreateItemRequest("전동드릴", null, "1", "10", start, end, List.of("6"));

        assertThat(idempotency.hashOf(a)).isEqualTo(idempotency.hashOf(same)).hasSize(64);
        assertThat(idempotency.hashOf(a)).isNotEqualTo(idempotency.hashOf(other));
        verify(records, never()).saveAndFlush(any());
    }
}
