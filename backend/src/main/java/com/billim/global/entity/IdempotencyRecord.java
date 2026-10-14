package com.billim.global.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long memberId;
    @Column(nullable = false, length = 150) private String operationKey;
    @Column(nullable = false, columnDefinition = "char(36)") private String requestKey;
    @Column(nullable = false, columnDefinition = "char(64)") private String requestHash;
    @Column(nullable = false) private int httpStatus;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json") private String responseBody;
    @Column(nullable = false) private Instant expiresAt;

    public static IdempotencyRecord succeeded(long memberId, String operationKey, String requestKey,
                                              String requestHash, int httpStatus, String responseBody,
                                              Instant expiresAt) {
        var record = new IdempotencyRecord();
        record.memberId = memberId;
        record.operationKey = operationKey;
        record.requestKey = requestKey;
        record.requestHash = requestHash;
        record.httpStatus = httpStatus;
        record.responseBody = responseBody;
        record.expiresAt = expiresAt;
        return record;
    }
}
