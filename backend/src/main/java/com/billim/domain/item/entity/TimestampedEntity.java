package com.billim.domain.item.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;

/**
 * created_at·updated_at 공통 컬럼. 애플리케이션이 UTC로 채운다. (ERD 1장)
 */
@MappedSuperclass
public abstract class TimestampedEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected void initTimestamps(Instant now) {
        this.createdAt = now;
        this.updatedAt = now;
    }

    protected void touch(Instant now) {
        this.updatedAt = now;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
