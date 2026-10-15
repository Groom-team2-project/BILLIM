package com.billim.domain.safety.dto;

/** 정책·안내 문서 (API 명세 PolicyPage). body는 일반 텍스트(Markdown)로 렌더링한다 */
public record PolicyPageResponse(
        String key,
        String title,
        String body,
        String versionLabel
) {
}
