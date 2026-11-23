package com.billim.domain.safety.service;

import com.billim.domain.safety.dto.PolicyPageResponse;
import com.billim.domain.safety.entity.PolicyKey;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 정책·안내 정적 문서 (API 명세 E_070). 공개 조회다.
 *
 * <p>문서는 {@code resources/policies/{key}.md}에 둔다. 형식은 머리말 두 줄 + 구분선 + 본문이다.
 * <pre>
 * title: 커뮤니티 규칙
 * version: 2026-10-13
 * ---
 * 본문...
 * </pre>
 *
 * <p>본문은 일반 텍스트(Markdown)로 내려주고 렌더링은 화면이 한다. 임의 HTML은 실행하지 않는다.
 * 파일이 없는 키는 404다 — 약관·개인정보는 별도 검토 후 파일을 추가한다.
 */
@Service
public class PolicyService {

    private static final String LOCATION = "policies/";
    private static final String HEADER_SEPARATOR = "---";
    private static final int BODY_MAX = 20_000;

    /** 정적 문서라 한 번 읽고 재사용한다. 파일이 바뀌면 재기동이 필요하다 */
    private final Map<PolicyKey, PolicyPageResponse> cache = new ConcurrentHashMap<>();

    public PolicyPageResponse find(String rawKey) {
        PolicyKey key = PolicyKey.from(rawKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        PolicyPageResponse cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        PolicyPageResponse loaded = load(key);
        cache.put(key, loaded);
        return loaded;
    }

    private PolicyPageResponse load(PolicyKey key) {
        Resource resource = new ClassPathResource(LOCATION + key.key() + ".md");
        if (!resource.exists()) {
            // 아직 작성하지 않은 문서
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        String document;
        try (InputStream input = resource.getInputStream()) {
            document = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 파일은 있는데 읽히지 않는 경우. 숨기지 않고 5xx로 드러낸다
            throw new IllegalStateException("정책 문서를 읽을 수 없습니다: " + key.key(), e);
        }

        return parse(key, document);
    }

    private PolicyPageResponse parse(PolicyKey key, String document) {
        String[] parts = document.split("(?m)^" + HEADER_SEPARATOR + "\\R", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("정책 문서 머리말이 없습니다: " + key.key());
        }

        String title = headerValue(parts[0], "title", key);
        String versionLabel = headerValue(parts[0], "version", key);
        String body = parts[1].strip();

        if (body.isEmpty()) {
            throw new IllegalStateException("정책 문서 본문이 비어 있습니다: " + key.key());
        }
        if (body.length() > BODY_MAX) {
            throw new IllegalStateException("정책 문서 본문이 응답 상한을 넘습니다: " + key.key());
        }

        return new PolicyPageResponse(key.key(), title, body, versionLabel);
    }

    private String headerValue(String header, String name, PolicyKey key) {
        for (String line : header.lines().toList()) {
            if (line.startsWith(name + ":")) {
                String value = line.substring(name.length() + 1).strip();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        throw new IllegalStateException("정책 문서에 %s가 없습니다: %s".formatted(name, key.key()));
    }
}
