package com.billim.domain.member.service;

import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.config.JpaAuditingConfig;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 표시 이름 수정의 권한·버전 규칙. Docker 필요 */
@DataJpaTest
@Import({MemberServiceDbTest.MySql.class, MemberService.class, JpaAuditingConfig.class})
class MemberServiceDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));   // compose.yaml과 같은 버전
        }
    }

    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    @Test
    @DisplayName("표시 이름을 바꾸면 version이 올라간다")
    void incrementsVersionOnChange() {
        Member member = memberRepository.save(Member.register("홍길동"));
        long before = member.getVersion();

        memberService.changeDisplayName(member.getId(), before, "김철수");
        em.flush();

        assertThat(member.getDisplayName()).isEqualTo("김철수");
        assertThat(member.getVersion()).isEqualTo(before + 1);
    }

    /** 응답의 version을 그대로 다시 보내 연속 수정이 가능해야 한다 */
    @Test
    @DisplayName("응답받은 version으로 곧바로 다시 수정할 수 있다")
    void allowsConsecutiveUpdates() {
        Member member = memberRepository.save(Member.register("홍길동"));

        Member first = memberService.changeDisplayName(member.getId(), member.getVersion(), "김철수");
        em.flush();
        Member second = memberService.changeDisplayName(member.getId(), first.getVersion(), "이영희");
        em.flush();

        assertThat(second.getDisplayName()).isEqualTo("이영희");
    }

    @Test
    @DisplayName("오래된 version으로 수정하면 409와 현재 version을 돌려준다")
    void rejectsStaleVersion() {
        Member member = memberRepository.save(Member.register("홍길동"));
        memberService.changeDisplayName(member.getId(), member.getVersion(), "김철수");
        em.flush();

        assertThatThrownBy(() -> memberService.changeDisplayName(member.getId(), 0L, "이영희"))
                .isInstanceOfSatisfying(VersionConflictException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT);
                    assertThat(e.getCurrentVersion()).isEqualTo(member.getVersion());
                });
    }

    /** 탈퇴 기능 미구현이라 상태를 직접 만든다. 구현되면 도메인 메서드로 교체 */
    @Test
    @DisplayName("탈퇴 회원은 수정할 수 없다")
    void rejectsWithdrawnMember() {
        Member member = memberRepository.save(Member.register("홍길동"));
        em.flush();
        em.createNativeQuery("update members set status = 'WITHDRAWN' where id = :id")
                .setParameter("id", member.getId())
                .executeUpdate();
        em.clear();

        assertThatThrownBy(() -> memberService.changeDisplayName(member.getId(), member.getVersion(), "김철수"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("없는 회원은 404")
    void rejectsUnknownMember() {
        assertThatThrownBy(() -> memberService.getMyProfile(999_999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("여러 회원의 요약을 ID로 묶어 조회한다")
    void returnsSummariesByIds() {
        Member hong = memberRepository.save(Member.register("홍길동"));
        Member kim = memberRepository.save(Member.register("김철수"));
        em.flush();

        Map<Long, MemberSummaryResponse> summaries =
                memberService.getSummaries(List.of(hong.getId(), kim.getId(), hong.getId()));

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(kim.getId()).displayName()).isEqualTo("김철수");
        assertThat(summaries.get(hong.getId()))
                .isEqualTo(new MemberSummaryResponse(
                        String.valueOf(hong.getId()), "홍길동", hong.getCreatedAt()));
    }

    @Test
    @DisplayName("빈 목록은 빈 Map")
    void returnsEmptyMapForEmptyIds() {
        assertThat(memberService.getSummaries(List.of())).isEmpty();
    }

    @Test
    @DisplayName("없는 ID는 결과에서 빠진다")
    void skipsUnknownIds() {
        Member hong = memberRepository.save(Member.register("홍길동"));
        em.flush();

        Map<Long, MemberSummaryResponse> summaries =
                memberService.getSummaries(List.of(hong.getId(), 999_999L));

        assertThat(summaries).containsOnlyKeys(hong.getId());
    }
}
