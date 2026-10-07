package com.billim.domain.member.entity;

import com.billim.global.entity.BaseTimeEntity;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** members */
@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    public static final int DISPLAY_NAME_MIN = 2;
    public static final int DISPLAY_NAME_MAX = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = DISPLAY_NAME_MAX)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    /** communities.id. FK 없음, 유효 소속 일치는 서비스 검사 */
    @Column
    private Long activeCommunityId;

    @Column
    private Instant withdrawnAt;

    @Version
    @Column(nullable = false)
    private Long version;

    private Member(String displayName) {
        this.displayName = requireValidDisplayName(displayName);
        this.role = MemberRole.USER;
        this.status = MemberStatus.ACTIVE;
    }

    /** 첫 소셜 로그인 가입 */
    public static Member register(String displayName) {
        return new Member(displayName);
    }

    private static String requireValidDisplayName(String value) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.length() < DISPLAY_NAME_MIN || trimmed.length() > DISPLAY_NAME_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "표시 이름은 %d~%d자로 입력해 주세요.".formatted(DISPLAY_NAME_MIN, DISPLAY_NAME_MAX));
        }
        return trimmed;
    }
}
