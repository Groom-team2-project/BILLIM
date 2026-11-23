package com.billim.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@link com.billim.global.entity.BaseTimeEntity}의 시각 자동 기록 활성화.
 * BillimApplication이 아닌 별도 설정 클래스에 두는 이유: @WebMvcTest 같은 슬라이스 테스트가
 * JPA 빈 없이 뜨도록 분리.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
