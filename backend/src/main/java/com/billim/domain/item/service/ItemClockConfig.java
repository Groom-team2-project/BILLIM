package com.billim.domain.item.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;

import java.time.Clock;

/** 시각 의존 로직(만료·KST 오늘)을 테스트에서 고정할 수 있게 Clock을 주입한다. */
@Configuration
class ItemClockConfig {

    @Bean
    @Fallback
    Clock itemClock() {
        return Clock.systemUTC();
    }
}
