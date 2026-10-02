package com.billim.domain.item.dto;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;

/** 검색 정렬. LATEST=createdAt DESC,id DESC / NEAREST=distance ASC,id DESC */
public enum ItemSort {
    LATEST, NEAREST;

    /** null은 기본값 LATEST. 그 외 지원하지 않는 값은 400 */
    public static ItemSort parse(String raw) {
        if (raw == null) {
            return LATEST;
        }
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 정렬입니다.");
        }
    }
}
