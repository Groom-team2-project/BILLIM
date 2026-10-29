package com.billim.domain.safety.service;

import com.billim.domain.safety.dto.MySanctionPageResponse;
import com.billim.domain.safety.dto.MySanctionResponse;
import com.billim.domain.safety.entity.ReportAction;
import com.billim.domain.safety.entity.Sanction;
import com.billim.domain.safety.repository.ReportActionRepository;
import com.billim.domain.safety.repository.SanctionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 내가 받은 운영 조치 내역 (API 명세 12.7 E_072).
 *
 * <h2>왜 필요한가</h2>
 * 처리 결과 알림은 제재 대상에게 조치 종류만 알리고 "자세한 내용은 내 프로필에서 확인해 주세요"로
 * 안내한다. 그런데 그 확인 경로가 없었다 — {@code MyProfile}에는 제한 여부 불리언 하나뿐이고
 * 제재 이력 조회는 ADMIN 전용이다. 명세 E_057이 "제재 대상에게는 최소 사유만 제공"이라고 정한
 * 그 최소 사유가 어디에도 닿지 않았다. 이 조회가 그 경로다.
 *
 * <h2>제재 테이블이 아니라 조치 테이블을 읽는다</h2>
 * 경고(WARN)는 제재 행을 만들지 않는다. {@code sanctions}만 보면 경고가 보이지 않는다.
 * 그래서 {@code report_actions}를 기준으로 읽고 제재 기간은 있을 때만 붙인다. (API 명세 13.3)
 *
 * @see com.billim.domain.safety.dto.MySanctionResponse 담지 않는 항목과 그 이유
 */
@Service
@Transactional(readOnly = true)
public class MySanctionService {

    private final ReportActionRepository reportActionRepository;
    private final SanctionRepository sanctionRepository;
    private final Clock clock;

    public MySanctionService(ReportActionRepository reportActionRepository,
                             SanctionRepository sanctionRepository,
                             Clock clock) {
        this.reportActionRepository = reportActionRepository;
        this.sanctionRepository = sanctionRepository;
        this.clock = clock;
    }

    /** 최근 조치가 먼저 온다. 반려는 빠진다 */
    public MySanctionPageResponse findMySanctions(long memberId, int page, int size) {
        Page<ReportAction> actions =
                reportActionRepository.findReceivedByMemberId(memberId, PageRequest.of(page, size));

        Map<Long, Sanction> sanctions = sanctionsByActionId(actions);
        Instant now = clock.instant();

        return new MySanctionPageResponse(
                actions.getContent().stream()
                        .map(action -> MySanctionResponse.of(action, sanctions.get(action.getId()), now))
                        .toList(),
                page, size, actions.getTotalElements(), actions.hasNext());
    }

    /** 조치별 제재를 한 번에 모아 붙인다. 건당 조회를 피한다 */
    private Map<Long, Sanction> sanctionsByActionId(Page<ReportAction> actions) {
        if (actions.isEmpty()) {
            return Map.of();
        }
        return sanctionRepository.findByReportActionIdIn(
                        actions.getContent().stream().map(ReportAction::getId).toList())
                .stream()
                .collect(Collectors.toMap(Sanction::getReportActionId, Function.identity()));
    }
}
