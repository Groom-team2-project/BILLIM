package com.billim.domain.item.controller;

import com.billim.domain.item.dto.CategoryListResponse;
import com.billim.domain.item.service.CategoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B_013 공통 카테고리 목록 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public CategoryListResponse list(Authentication authentication) {
        AuthenticatedMember.id(authentication);   // 로그인 회원만
        return categoryService.list();
    }
}
