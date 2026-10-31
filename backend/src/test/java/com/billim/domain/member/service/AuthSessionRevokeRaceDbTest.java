package com.billim.domain.member.service;

import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.AuthSessionRepository;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.config.JpaAuditingConfig;
import com.billim.global.security.session.SessionTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 다른 기기 로그아웃과 유휴 만료 연장의 경합.
 * 연장 요청이 세션을 읽은 뒤 일괄 폐기가 먼저 커밋되어도, 늦게 커밋되는 연장이 폐기를 되돌리면 안 됨.
 * 실제 커밋 순서를 재현해야 해서 테스트 트랜잭션 미사용. Docker 필요
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AuthSessionRevokeRaceDbTest.MySql.class, JpaAuditingConfig.class})
class AuthSessionRevokeRaceDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));   // compose.yaml과 같은 버전
        }
    }

    @Autowired AuthSessionRepository authSessionRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("폐기 전에 읽은 세션의 연장이 늦게 커밋되어도 폐기가 유지된다")
    void lateTouchDoesNotResurrectRevokedSession() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        Member member = memberRepository.save(Member.register("경합회원"));
        AuthSession current = authSessionRepository.save(
                AuthSession.forMember(member, SessionTokens.hash("current"), "c", past, Duration.ofHours(2), Duration.ofHours(24)));
        authSessionRepository.save(
                AuthSession.forMember(member, SessionTokens.hash("other"), "o", past, Duration.ofHours(2), Duration.ofHours(24)));

        // 다른 기기 요청: 세션 조회 후 유휴 만료 연장 — 아직 커밋 전
        TransactionStatus touching = transactionManager.getTransaction(new DefaultTransactionDefinition());
        AuthSession other = authSessionRepository.findWithMember(SessionTokens.hash("other")).orElseThrow();
        other.touch(Instant.now(), Duration.ofHours(2));

        // 그사이 현재 기기의 다른 기기 로그아웃이 먼저 커밋
        TransactionTemplate revoking = new TransactionTemplate(transactionManager);
        revoking.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        int revoked = revoking.execute(status ->
                authSessionRepository.revokeOthers(member.getId(), current.getId(), Instant.now()));

        transactionManager.commit(touching);

        assertThat(revoked).isEqualTo(1);
        assertThat(authSessionRepository.findWithMember(SessionTokens.hash("other")).orElseThrow().getRevokedAt())
                .isNotNull();
    }
}
