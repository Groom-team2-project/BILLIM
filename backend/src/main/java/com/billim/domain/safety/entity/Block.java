package com.billim.domain.safety.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * blocks — 방향성 차단 관계.
 *
 * <p>행은 한 방향이지만 노출·채팅 제한 판정은 양방향 OR이다. 내가 해제해도 상대가 나를
 * 차단한 상태면 제한이 계속된다. (ERD 4.7, 정책 및 상태 5 차단)
 *
 * <p>차단은 신규 관계만 막는다. 진행 중 거래는 반납 처리 경로를 위해 유지한다.
 */
@Entity
@Table(
        name = "blocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_blocks_blocker_blocked", columnNames = {"blocker_id", "blocked_id"}),
        indexes = @Index(name = "idx_blocks_blocked_blocker", columnList = "blocked_id,blocker_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false, updatable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false, updatable = false)
    private Long blockedId;

    public static Block of(long blockerId, long blockedId) {
        if (blockerId == blockedId) {
            throw new IllegalArgumentException("자기 자신은 차단할 수 없습니다.");
        }
        Block block = new Block();
        block.blockerId = blockerId;
        block.blockedId = blockedId;
        return block;
    }
}
