package com.billim.domain.member.service;

import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.member.entity.MemberStatus;
import com.billim.domain.member.entity.SocialAccount;
import com.billim.domain.member.entity.SocialProvider;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.member.repository.SocialAccountRepository;
import com.billim.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 MySQL(Testcontainers)에서 첫 가입·재로그인 분기와 UNIQUE 제약을 확인.
 */
@DataJpaTest
@Import({MemberAuthServiceDbTest.MySql.class, MemberAuthService.class, JpaAuditingConfig.class})
class MemberAuthServiceDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));   // compose.yaml과 같은 버전
        }
    }

    private static final String KAKAO_SUBJECT = "1234567890";

    @Autowired MemberAuthService memberAuthService;
    @Autowired MemberRepository memberRepository;
    @Autowired SocialAccountRepository socialAccountRepository;

    @Test
    @DisplayName("첫 로그인은 회원과 소셜 계정을 함께 생성")
    void registersOnFirstLogin() {
        Member member = memberAuthService.loginOrRegister(SocialProvider.KAKAO, KAKAO_SUBJECT, "홍길동");

        assertThat(member.getId()).isNotNull();
        assertThat(member.getDisplayName()).isEqualTo("홍길동");
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getActiveCommunityId()).isNull();   // 동네 인증 전
        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(socialAccountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("재로그인은 기존 회원을 반환하고 새로 가입시키지 않는다")
    void reusesMemberOnSecondLogin() {
        Member first = memberAuthService.loginOrRegister(SocialProvider.KAKAO, KAKAO_SUBJECT, "홍길동");
        Member second = memberAuthService.loginOrRegister(SocialProvider.KAKAO, KAKAO_SUBJECT, "바뀐닉네임");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(socialAccountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("재로그인은 제공자 닉네임으로 표시 이름을 덮어쓰지 않는다")
    void keepsDisplayNameOnSecondLogin() {
        memberAuthService.loginOrRegister(SocialProvider.KAKAO, KAKAO_SUBJECT, "홍길동");
        Member second = memberAuthService.loginOrRegister(SocialProvider.KAKAO, KAKAO_SUBJECT, "바뀐닉네임");

        assertThat(second.getDisplayName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("다른 제공자 식별자는 별도 회원으로 가입")
    void registersDifferentSubjectAsNewMember() {
        memberAuthService.loginOrRegister(SocialProvider.KAKAO, KAKAO_SUBJECT, "홍길동");
        memberAuthService.loginOrRegister(SocialProvider.KAKAO, "1234500000", "다른사람");

        assertThat(memberRepository.count()).isEqualTo(2);
        assertThat(socialAccountRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("닉네임 미동의(null)도 가입에 성공")
    void registersWithoutNickname() {
        Member member = memberAuthService.loginOrRegister(SocialProvider.KAKAO, KAKAO_SUBJECT, null);

        assertThat(member.getDisplayName())
                .hasSizeBetween(Member.DISPLAY_NAME_MIN, Member.DISPLAY_NAME_MAX);
    }

    @Test
    @DisplayName("JPA Auditing이 created_at·updated_at을 채운다")
    void auditingFillsTimestamps() {
        Member member = memberAuthService.loginOrRegister(SocialProvider.KAKAO, KAKAO_SUBJECT, "홍길동");

        assertThat(member.getCreatedAt()).isNotNull();
        assertThat(member.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 제공자 식별자 중복 저장은 UNIQUE 제약으로 거부 — 재시도 로직의 근거")
    void rejectsDuplicateProviderSubject() {
        Member member = memberAuthService.loginOrRegister(SocialProvider.KAKAO, KAKAO_SUBJECT, "홍길동");
        SocialAccount duplicate = SocialAccount.link(member, SocialProvider.KAKAO, KAKAO_SUBJECT);

        assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
