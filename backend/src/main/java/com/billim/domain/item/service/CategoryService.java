package com.billim.domain.item.service;

import com.billim.domain.item.dto.CategoryListResponse;
import com.billim.domain.item.dto.CategoryResponse;
import com.billim.domain.item.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public CategoryListResponse list() {
        return new CategoryListResponse(categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList());
    }
}
