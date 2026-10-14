package com.billim.domain.safety.service;

import com.billim.domain.safety.dto.PolicyPageResponse;
import com.billim.domain.safety.entity.PolicyKey;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 정책·안내 정적 문서 (API 명세 E_070) */
class PolicyServiceTest {

    private final PolicyService policyService = new PolicyService();

    @ParameterizedTest
    @ValueSource(strings = {"community", "safety", "prohibited", "damage", "disputes", "trust"})
    @DisplayName("작성된 문서는 제목·본문·버전을 내려준다")
    void writtenDocumentsAreServed(String key) {
        PolicyPageResponse response = policyService.find(key);

        assertThat(response.key()).isEqualTo(key);
        assertThat(response.title()).isNotBlank();
        assertThat(response.body()).isNotBlank();
        assertThat(response.versionLabel()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"terms", "privacy", "support", "about"})
    @DisplayName("아직 작성하지 않은 문서는 404다")
    void unwrittenDocumentsAreNotFound(String key) {
        // 약관·개인정보는 별도 검토 후 파일을 추가한다 (API 명세 E_070)
        assertThatThrownBy(() -> policyService.find(key))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nope", "COMMUNITY!", "../application", ""})
    @DisplayName("정의되지 않은 키는 404다")
    void unknownKeyIsNotFound(String key) {
        assertThatThrownBy(() -> policyService.find(key))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("대소문자가 섞인 키도 받아들인다")
    void keyIsCaseInsensitive() {
        assertThat(policyService.find("COMMUNITY").key()).isEqualTo("community");
        assertThat(policyService.find(" Trust ").key()).isEqualTo("trust");
    }

    @Test
    @DisplayName("본문에 머리말이 섞여 들어가지 않는다")
    void bodyExcludesHeader() {
        PolicyPageResponse response = policyService.find("community");

        assertThat(response.title()).isEqualTo("커뮤니티 규칙");
        assertThat(response.body()).doesNotContain("title:").doesNotContain("version:");
    }

    @Test
    @DisplayName("같은 키를 다시 조회해도 같은 내용을 돌려준다")
    void repeatedLookupIsStable() {
        assertThat(policyService.find("safety")).isEqualTo(policyService.find("safety"));
    }

    @Test
    @DisplayName("명세의 키 10개가 모두 정의돼 있다")
    void allSpecKeysAreDefined() {
        assertThat(PolicyKey.values()).hasSize(10);
        assertThat(PolicyKey.from("disputes")).isPresent();
    }
}
