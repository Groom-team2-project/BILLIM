package com.billim.domain.item.dto;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemSortTest {

    @Test
    @DisplayName("생략하면 LATEST다")
    void defaultsToLatest() {
        assertThat(ItemSort.parse(null)).isEqualTo(ItemSort.LATEST);
    }

    @Test
    @DisplayName("지원하는 값을 파싱한다")
    void parsesSupportedValues() {
        assertThat(ItemSort.parse("NEAREST")).isEqualTo(ItemSort.NEAREST);
        assertThat(ItemSort.parse("LATEST")).isEqualTo(ItemSort.LATEST);
    }

    @Test
    @DisplayName("지원하지 않는 정렬은 400이다")
    void rejectsUnsupportedValues() {
        assertThatThrownBy(() -> ItemSort.parse("OLDEST")).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> ItemSort.parse("latest")).isInstanceOf(BusinessException.class);
    }
}
