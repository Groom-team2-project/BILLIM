package com.billim.domain.item.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** 물건 사진과 표시 순서. sort_order 0이 대표 사진. */
@Entity
@Table(name = "item_images")
public class ItemImage extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "media_file_id", nullable = false)
    private Long mediaFileId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ItemImage() {
    }

    ItemImage(Item item, Long mediaFileId, int sortOrder, Instant now) {
        this.item = item;
        this.mediaFileId = mediaFileId;
        this.sortOrder = sortOrder;
        initTimestamps(now);
    }

    public Long getId() {
        return id;
    }

    public Long getMediaFileId() {
        return mediaFileId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isRepresentative() {
        return sortOrder == 0;
    }
}
