package com.billim.domain.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 사건 소비 주기 실행에만 쓰는 스케줄링. 끄면 @Scheduled 전체가 동작하지 않는다 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "billim.notification.outbox.enabled", matchIfMissing = true)
class NotificationSchedulingConfig {
}
