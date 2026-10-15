package com.billim.domain.item.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;

import java.time.Clock;

// 시각 의존 로직(만료·KST 오늘)의 테스트 고정을 위한 Clock 주입
@Configuration
class ItemClockConfig {

    @Bean
    @Fallback
    Clock itemClock() {
        return Clock.systemUTC();
    }
}
