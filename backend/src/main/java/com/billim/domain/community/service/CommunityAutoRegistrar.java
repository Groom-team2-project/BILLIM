package com.billim.domain.community.service;

import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.Region;
import com.billim.domain.community.repository.CommunityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역지오코딩으로 찾은 읍면동을 동네로 등록한다.
 *
 * <p>전국 읍면동을 미리 적재하는 대신 처음 쓰이는 동만 만든다. 행정안전부 코드 목록에는 중심 좌표가
 * 없어 3,500개를 따로 지오코딩해야 하는데, 지도 응답에 코드·이름·대표 좌표가 모두 들어 있어
 * 같은 결과를 데이터 출처 문제 없이 얻는다. 행 수는 코드 유니크 키가 전국 읍면동 수로 묶는다.
 * (도메인 가이드 결정 '법정동 위치 인증')
 *
 * <p>자기 트랜잭션에서 커밋한다. 호출 측 조회 트랜잭션에 섞이면 유니크 충돌 한 번이 조회 전체를
 * 실패시킨다. 같은 동에서 동시에 들어오면 먼저 커밋한 행을 쓰고 이쪽은 되돌린다.
 */
@Component
public class CommunityAutoRegistrar {

    private static final Logger log = LoggerFactory.getLogger(CommunityAutoRegistrar.class);

    private final CommunityRepository communityRepository;

    /** 자동 등록 동네의 인증 반경(m). 읍면동 코드가 1차 판정이고 이 반경은 폴백용이다 */
    private final int defaultRadiusM;

    public CommunityAutoRegistrar(
            CommunityRepository communityRepository,
            @Value("${billim.community.verification.region.default-radius-m:3000}") int defaultRadiusM) {
        this.communityRepository = communityRepository;
        this.defaultRadiusM = defaultRadiusM;
    }

    /**
     * 읍면동을 동네로 저장한다. 유니크 충돌은 호출 측이 다시 조회해 처리한다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 같은 읍면동이 이미 등록된 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Community register(Region region) {
        Community saved = communityRepository.saveAndFlush(
                Community.ofRegion(region, defaultRadiusM));

        log.info("읍면동 동네 자동 등록: communityId={} regionCode={} name={}",
                saved.getId(), region.code(), region.name());
        return saved;
    }
}
