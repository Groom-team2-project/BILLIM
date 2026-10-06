package com.billim.domain.item.repository;

import com.billim.domain.item.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** active 분류를 sortOrder, id 순으로 */
    List<Category> findByActiveTrueOrderBySortOrderAscIdAsc();
}
