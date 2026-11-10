package com.billim.domain.community.service;

import com.billim.domain.community.dto.MembershipResponse;
import com.billim.domain.community.dto.VerifyLocationRequest;
import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityMembership;
import com.billim.domain.community.entity.MembershipStatus;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 위치 인증과 활성 동네 선택 (API 명세 E_011).
 *
 * <p>저장하는 것은 반경 확인 결과·동네·시점뿐이다. 요청 좌표와 정확도는 판정에만 쓰고
 * DB·로그에 남기지 않는다. (정책 및 상태 5 위치 인증)
 *
 * <p>판정은 두 기준을 병행한다. 현재 좌표의 행정동이 동네의 행정동과 같으면 통과하고,
 * 아니면 기존 좌표 반경 판정으로 다시 본다. 행정동 쪽이 생활권과 더 잘 맞고, 지도 연동이
 * 죽거나 코드가 없는 동네에서도 반경으로 가입이 계속된다. (도메인 가이드 결정 '행정동 위치 인증')
 *
 * <p>위치 확인은 해당 동네에 지금 있다는 확인이다. 실제 거주·재직 증명이 아니다.
 * (도메인 가이드 결정 '위치 인증의 의미')
 */
@Service
public class CommunityVerificationService {

    private static final Logger log = LoggerFactory.getLogger(CommunityVerificationService.class);

    private final CommunityQueryService communityQueryService;
    private final CommunityMembershipRepository membershipRepository;
    private final CurrentCommunityStore currentCommunityStore;
    private final RegionResolver regionResolver;
    private final Clock clock;

    /** 허용 정확도 상한(m). 초과하면 422 LOCATION_INACCURATE (에러 코드 — community) */
    private final double maxAccuracyMeters;

    /** 반경 판정에 정확도를 유리하게 반영할지 */
    private final boolean accuracyTolerance;

    public CommunityVerificationService(
            CommunityQueryService communityQueryService,
            CommunityMembershipRepository membershipRepository,
            CurrentCommunityStore currentCommunityStore,
            RegionResolver regionResolver,
            Clock clock,
            @Value("${billim.community.verification.max-accuracy-meters:5000}") double maxAccuracyMeters,
            @Value("${billim.community.verification.accuracy-tolerance:true}") boolean accuracyTolerance) {
        this.communityQueryService = communityQueryService;
        this.membershipRepository = membershipRepository;
        this.currentCommunityStore = currentCommunityStore;
        this.regionResolver = regionResolver;
        this.clock = clock;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.accuracyTolerance = accuracyTolerance;
    }

    /**
     * 선택한 동네를 위치로 확인하고 활성 소속으로 만든다. 최초·재인증 모두 200이다.
     *
     * <p>활성 소속은 회원당 하나라 회원 행 잠금 아래에서 이전 소속을 이탈시키고 새 소속을 세운다.
     */
    @Transactional
    public MembershipResponse verify(long memberId, long communityId, VerifyLocationRequest location) {
        Community community = communityQueryService.requireActive(communityId);
        Method method = requireVerified(community, location);

        currentCommunityStore.lock(memberId);
        Instant now = clock.instant();

        CommunityMembership membership = joinOrReverify(memberId, communityId, now);
        // 잠금 안에서 DB에 반영한다. 슬롯 유니크(V23) 위반을 여기서 받아야 한다
        membershipRepository.flush();

        // 방금 인증한 동네를 현재 동네로 삼는다. 저장 동네가 둘이어도 피드 기준은 하나다
        currentCommunityStore.select(memberId, communityId, now);

        // 좌표·정확도는 남기지 않는다. 어느 동네를 언제 어떤 기준으로 확인했는지만 기록한다
        log.info("동네 인증: memberId={} communityId={} 판정={}", memberId, communityId, method);
        return MembershipResponse.of(membership, community, now);
    }

    /**
     * 행정동이 같으면 통과시키고, 아니면 반경으로 다시 본다.
     *
     * <p>행정동 판정에는 정확도 상한을 걸지 않는다. 좌표 한 점이 어느 행정동에 있는지만 묻기 때문에
     * 측위 오차와 무관하다. 정확도는 반경 판정을 신뢰할 수 있는지의 문제라 폴백 경로에서만 본다.
     */
    private Method requireVerified(Community community, VerifyLocationRequest location) {
        if (matchesRegion(community, location)) {
            return Method.REGION;
        }
        requireAccurate(location);
        requireWithinRadius(community, location);
        return Method.RADIUS;
    }

    /**
     * 현재 좌표의 행정동이 이 동네의 행정동인지.
     *
     * <p>코드가 없는 동네는 지도를 부르지 않는다. 비교할 대상이 없어 결과가 정해져 있다.
     * 지도 조회가 실패하면 빈 값이 와서 false가 되고 반경 판정으로 내려간다.
     */
    private boolean matchesRegion(Community community, VerifyLocationRequest location) {
        if (community.getRegionCode() == null) {
            return false;
        }
        return regionResolver.resolve(location.latitude(), location.longitude())
                .map(region -> community.matchesRegion(region.code()))
                .orElse(false);
    }

    /** 정확도가 낮으면 반경 판정을 신뢰할 수 없다 */
    private void requireAccurate(VerifyLocationRequest location) {
        if (location.accuracyMeters() > maxAccuracyMeters) {
            throw new BusinessException(ErrorCode.LOCATION_INACCURATE);
        }
    }

    /**
     * 반경 밖이면 인증되지 않는다. 거리 값은 응답·로그에 담지 않는다.
     *
     * <p>정확도 보정을 켜면 측위 오차만큼 너그럽게 본다. 기기가 "이 좌표에서 오차 R" 이라고
     * 알려준 것이므로, 반경 경계 근처에서 실제로는 안에 있는 사람을 밖으로 몰지 않는다.
     * 오차가 클수록 느슨해지므로 정확도 상한이 함께 걸려 있어야 의미가 있다.
     */
    private void requireWithinRadius(Community community, VerifyLocationRequest location) {
        double meters = Distances.metersBetween(
                location.latitude(), location.longitude(),
                community.centerLatitudeValue(), community.centerLongitudeValue());

        double effective = accuracyTolerance
                ? Math.max(0, meters - location.accuracyMeters())
                : meters;

        if (effective > community.getVerificationRadiusM()) {
            throw new BusinessException(ErrorCode.LOCATION_OUTSIDE);
        }
    }

    /** 어떤 기준으로 통과했는지. 운영 로그용이며 응답에는 담지 않는다 */
    private enum Method {
        REGION, RADIUS
    }

    /**
     * 저장 동네에 더하거나, 이미 저장된 동네면 인증만 갱신한다.
     *
     * <p>예전에는 다른 동네를 인증하면 이전 소속을 이탈시켰다. 이제 둘까지 쌓이고, 셋째는
     * 거부한다 — 어느 동네를 뺄지는 회원이 정할 일이다(E_076 이탈). 자동으로 밀어내면
     * 모르는 사이에 그 동네 거래 권한을 잃는다.
     *
     * <p>같은 동네를 다시 인증하면 새 행을 만들지 않고 기존 행을 갱신한다 (ERD 4.2).
     */
    private CommunityMembership joinOrReverify(long memberId, long communityId, Instant now) {
        List<CommunityMembership> saved = membershipRepository
                .findByMemberIdAndStatusOrderByActiveSlotAsc(memberId, MembershipStatus.ACTIVE);
        Optional<CommunityMembership> existing =
                membershipRepository.findByMemberIdAndCommunityId(memberId, communityId);

        Optional<CommunityMembership> alreadySaved =
                existing.filter(CommunityMembership::isActive);
        if (alreadySaved.isPresent()) {
            CommunityMembership membership = alreadySaved.get();
            membership.reverify(now);
            return membership;
        }
        if (saved.size() >= CommunityMembership.MAX_ACTIVE) {
            throw new BusinessException(ErrorCode.COMMUNITY_LIMIT_EXCEEDED);
        }

        int slot = freeSlot(saved);
        if (existing.isPresent()) {
            CommunityMembership membership = existing.get();
            membership.rejoin(slot, now);
            return membership;
        }
        return membershipRepository.save(
                CommunityMembership.verified(memberId, communityId, slot, now));
    }

    /** 쓰이지 않는 가장 작은 슬롯. 상한 검사를 지난 뒤에만 부른다 */
    private int freeSlot(List<CommunityMembership> saved) {
        for (int slot = 1; slot <= CommunityMembership.MAX_ACTIVE; slot++) {
            int candidate = slot;
            boolean taken = saved.stream()
                    .anyMatch(membership -> candidate == membership.getActiveSlot());
            if (!taken) {
                return slot;
            }
        }
        throw new IllegalStateException("빈 슬롯이 없다. 상한 검사를 건너뛰었다");
    }
}
