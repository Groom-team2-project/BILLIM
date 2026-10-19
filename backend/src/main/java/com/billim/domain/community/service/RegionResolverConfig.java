package com.billim.domain.community.service;

import com.billim.global.json.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * 행정동 역지오코딩 연동 설정.
 *
 * <p>키가 없거나 꺼져 있으면 항상 빈 값을 주는 구현을 쓴다. 로컬·테스트·CI에는 지도 키가 없고,
 * 그 환경에서도 위치 인증이 반경 판정으로 동작해야 한다.
 */
@Configuration
public class RegionResolverConfig {

    private static final Logger log = LoggerFactory.getLogger(RegionResolverConfig.class);

    /** 카카오 REST API 키 인증 헤더 접두어 */
    private static final String AUTH_PREFIX = "KakaoAK ";

    @Bean
    public RegionResolver regionResolver(
            JsonCodec jsonCodec,
            @Value("${billim.community.verification.region.enabled:true}") boolean enabled,
            @Value("${billim.community.verification.region.api-key:}") String apiKey,
            @Value("${billim.community.verification.region.base-url:https://dapi.kakao.com}") String baseUrl,
            @Value("${billim.community.verification.region.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${billim.community.verification.region.read-timeout-ms:2000}") int readTimeoutMs) {

        if (!enabled || apiKey.isBlank()) {
            log.info("행정동 역지오코딩 미사용. 위치 인증은 반경 판정만 사용한다 (enabled={}, 키 설정={})",
                    enabled, !apiKey.isBlank());
            return (latitude, longitude) -> Optional.empty();
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, AUTH_PREFIX + apiKey)
                .requestFactory(requestFactory)
                .build();

        log.info("행정동 역지오코딩 사용. baseUrl={}", baseUrl);
        return new KakaoRegionResolver(restClient, jsonCodec);
    }
}
