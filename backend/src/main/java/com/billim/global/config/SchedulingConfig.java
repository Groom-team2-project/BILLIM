package com.billim.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * @Scheduled 활성화. 슬라이스 테스트가 스케줄러 없이 뜨도록 별도 설정 클래스로 분리.
 *
 * <p>@SchedulerLock 작업의 인스턴스 간 잠금 저장소는 MySQL(shedlock 테이블).
 * Redis는 미사용 구성이 있어 제외. 잠금 시각은 DB 시계 기준 — 인스턴스 간 시계 차이 배제.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
