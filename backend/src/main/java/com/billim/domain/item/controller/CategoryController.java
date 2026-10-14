package com.billim.domain.item.controller;

import com.billim.domain.item.dto.CategoryListResponse;
import com.billim.domain.item.service.CategoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** B_013 카테고리 목록. 로그인 회원만, 활성 카테고리를 sortOrder 순 */
    @GetMapping
    public CategoryListResponse list(Authentication authentication) {
        AuthenticatedMember.id(authentication);
        return categoryService.list();
    }
}
