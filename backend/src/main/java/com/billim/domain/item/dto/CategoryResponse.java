package com.billim.domain.item.dto;

import com.billim.domain.item.entity.Category;

public record CategoryResponse(String id, String code, String name, int sortOrder) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(String.valueOf(c.getId()), c.getCode(), c.getName(), c.getSortOrder());
    }
}
