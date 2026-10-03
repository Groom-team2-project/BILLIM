package com.billim.domain.item.dto;

import com.billim.domain.item.entity.MediaFile;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** 사진 업로드 응답 (API 명세 MediaFile). storage_key는 노출하지 않는다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaFileResponse(
        String id,
        String mimeType,
        long byteSize,
        int width,
        int height,
        String contentUrl,
        String status,
        Instant expiresAt
) {

    public static String contentUrl(Long mediaId) {
        return "/api/v1/media/" + mediaId + "/content";
    }

    public static MediaFileResponse from(MediaFile m) {
        return new MediaFileResponse(
                String.valueOf(m.getId()), m.getMimeType(), m.getByteSize(), m.getWidth(), m.getHeight(),
                contentUrl(m.getId()), m.getStatus().name(),
                m.getStatus() == com.billim.domain.item.entity.MediaStatus.TEMP ? m.getExpiresAt() : null);
    }
}
