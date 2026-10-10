package com.billim.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 활성화. 슬라이스 테스트가 스케줄러 없이 뜨도록 별도 설정 클래스로 분리.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
