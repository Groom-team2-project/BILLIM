package com.billim.domain.member.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** social_accounts */
@Entity
@Table(name = "social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    /** 제공자 불변 식별자. 카카오 회원번호이며 members.id와 무관 */
    @Column(nullable = false, length = 100)
    private String providerSubject;

    private SocialAccount(Member member, SocialProvider provider, String providerSubject) {
        this.member = member;
        this.provider = provider;
        this.providerSubject = providerSubject;
    }

    /** 가입 시 소셜 계정 연결 */
    public static SocialAccount link(Member member, SocialProvider provider, String providerSubject) {
        return new SocialAccount(member, provider, providerSubject);
    }
}
