package com.billim.domain.item.service;

import com.billim.global.entity.IdempotencyRecord;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.json.JsonCodec;
import com.billim.global.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

// POST 재전송 처리 (API 명세 0.3). 같은 회원·작업·키로 성공한 요청은 저장된 응답 재사용, 내용이 다르면 409
// 호출 전 회원 행 잠금으로 같은 회원의 동시 요청 직렬화 (대여 RentalRequestEffects와 같은 방식)
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ItemIdempotency {

    static final String CREATE_ITEM = "POST /api/v1/items";
    static final String UPLOAD_MEDIA = "POST /api/v1/media";
    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyRecordRepository records;
    private final JsonCodec json;

    public ItemIdempotency(IdempotencyRecordRepository records, JsonCodec json) {
        this.records = records;
        this.json = json;
    }

    // 저장된 성공 응답. 없거나 만료면 빈 값(만료 기록은 삭제), 키가 같고 요청 내용이 다르면 409
    public <T> Optional<T> replay(long memberId, String operation, String key, String requestHash,
                                  int status, Class<T> type, Instant now) {
        Optional<IdempotencyRecord> found = records.findByMemberIdAndOperationKeyAndRequestKey(memberId, operation, key);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyRecord record = found.get();
        if (!record.getExpiresAt().isAfter(now)) {
            records.delete(record);
            records.flush();
            return Optional.empty();
        }
        if (!record.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (record.getHttpStatus() != status) {
            throw new IllegalStateException("저장된 성공 응답 코드 불일치");
        }
        return Optional.of(json.read(record.getResponseBody(), type));
    }

    // 성공 응답 24시간 보관
    public void record(long memberId, String operation, String key, String requestHash,
                       int status, Object response, Instant now) {
        records.saveAndFlush(IdempotencyRecord.succeeded(memberId, operation, key, requestHash,
                status, json.write(response), now.plus(TTL)));
    }

    // JSON 요청 본문의 SHA-256
    public String hashOf(Object body) {
        return sha256(json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    // 업로드 파일의 SHA-256 (선언 MIME + 파일 내용)
    public static String hashOfFile(String declaredMime, byte[] data) {
        byte[] mime = String.valueOf(declaredMime).getBytes(StandardCharsets.UTF_8);
        byte[] joined = new byte[mime.length + 1 + data.length];
        System.arraycopy(mime, 0, joined, 0, mime.length);
        System.arraycopy(data, 0, joined, mime.length + 1, data.length);
        return sha256(joined);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 사용 불가", e);
        }
    }
}
