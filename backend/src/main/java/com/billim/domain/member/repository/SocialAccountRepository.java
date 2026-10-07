package com.billim.domain.member.repository;

import com.billim.domain.member.entity.SocialAccount;
import com.billim.domain.member.entity.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    /** 재로그인 조회. member를 함께 읽어 추가 조회 방지 */
    @Query("""
            select sa from SocialAccount sa
            join fetch sa.member
            where sa.provider = :provider and sa.providerSubject = :providerSubject
            """)
    Optional<SocialAccount> findWithMember(SocialProvider provider, String providerSubject);
}
