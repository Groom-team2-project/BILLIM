package com.billim.domain.safety.service;

import com.billim.domain.item.dto.CategoryResponse;
import com.billim.domain.safety.dto.AdminStatisticsResponse;
import com.billim.domain.safety.dto.CategoryCountResponse;
import com.billim.domain.safety.dto.DailyCountResponse;
import com.billim.domain.safety.dto.PopularItemResponse;
import com.billim.domain.safety.repository.AdminStatisticsRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 동네 운영 통계 (API 명세 13.6 E_069).
 *
 * <h2>집계 정의</h2>
 * 명세가 가리키는 '설계 규칙 7절'이 없어 여기에서 정하고 도메인 가이드에 남긴다.
 * (도메인 가이드 결정 '운영 통계')
 * <ul>
 *   <li>누적(조회 시점): 등록 물건·활성 대여·미처리 신고·카테고리별 물건</li>
 *   <li>기간(from~to): 신규 물건·신규 이웃·요청 수·성사 수·성사율·일자별 요청·인기 물건</li>
 *   <li>기간 지표는 모두 <b>그 기간에 생성된 대여 요청</b>을 같은 모집단으로 쓴다.
 *       성사율의 분자와 인기 물건이 같은 집합이라 두 수치가 서로 설명된다.</li>
 *   <li>물건은 논리 삭제만 제외한다. 숨긴 물건도 운영자에게는 등록된 물건이다.</li>
 *   <li>카테고리 합계는 등록 총수와 같다. 같은 조건으로 세고 분류를 빼지 않는다.</li>
 * </ul>
 *
 * <h2>한계</h2>
 * <ul>
 *   <li>성사율 분모에 아직 진행 중인 요청이 들어간다. 기간 끝에 들어온 요청은 반납 전이라
 *       비율을 낮춘다. 명세가 요청 수·성사 수·성사율 세 값을 함께 요구하므로, 분모에서
 *       진행 중을 빼서 세 값이 서로 안 맞는 쪽보다 이 정의를 택했다.</li>
 *   <li>신규 이웃은 재합류를 세지 않는다. 인증 재시도가 같은 소속 행을 갱신한다. (ERD 4.2)</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminStatisticsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 기간 상한. 명세 13.6 — from/to 최대 31일 */
    private static final int MAX_DAYS = 31;

    /** 인기 물건 표시 수. 프로토타입 '인기 물건 TOP 3' */
    private static final int POPULAR_LIMIT = 3;

    private final AdminStatisticsRepository repository;

    public AdminStatisticsService(AdminStatisticsRepository repository) {
        this.repository = repository;
    }

    public AdminStatisticsResponse findStatistics(long communityId, LocalDate from, LocalDate to) {
        validatePeriod(from, to);
        if (repository.countCommunity(communityId) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // KST 달력 날짜 양끝 포함 → UTC instant 반열린 구간 [fromAt, toAt)
        Instant fromAt = from.atStartOfDay(KST).toInstant();
        Instant toAt = to.plusDays(1).atStartOfDay(KST).toInstant();

        List<AdminStatisticsRepository.DailyRentalRow> daily =
                repository.findDailyRentals(communityId, fromAt, toAt);
        long requests = 0;
        long returned = 0;
        for (AdminStatisticsRepository.DailyRentalRow row : daily) {
            requests += row.getRequestCount();
            returned += row.getReturnedCount();
        }
        int requestCount = toInt(requests);
        int successfulRequestCount = toInt(returned);

        return new AdminStatisticsResponse(
                String.valueOf(communityId),
                from,
                to,
                toInt(repository.countRegisteredItems(communityId)),
                toInt(repository.countActiveRentals(communityId)),
                toInt(repository.countNewItems(communityId, fromAt, toAt)),
                toInt(repository.countNewMembers(communityId, fromAt, toAt)),
                requestCount,
                successfulRequestCount,
                successRate(requestCount, successfulRequestCount),
                toInt(repository.countPendingReports(communityId)),
                fillMissingDays(from, to, daily),
                categoryCounts(communityId),
                popularItems(communityId, fromAt, toAt));
    }

    /** from<=to, 상한 31일. 어기면 400 */
    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    /** 요청 대비 성사 비율. 소수 첫째 자리까지. 요청이 없으면 0 */
    private double successRate(int requestCount, int successfulRequestCount) {
        if (requestCount == 0) {
            return 0;
        }
        return Math.round(successfulRequestCount * 1000.0 / requestCount) / 10.0;
    }

    /**
     * 요청이 없던 날을 0으로 채운다. 그래프가 날짜를 건너뛰면 추세가 왜곡된다.
     *
     * <p>기간이 최대 31일이라 날짜를 모두 만들어도 31개다.
     */
    private List<DailyCountResponse> fillMissingDays(
            LocalDate from, LocalDate to, List<AdminStatisticsRepository.DailyRentalRow> rows) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (AdminStatisticsRepository.DailyRentalRow row : rows) {
            counts.put(row.getStatDate(), row.getRequestCount());
        }
        List<DailyCountResponse> filled = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            filled.add(new DailyCountResponse(date, toInt(counts.getOrDefault(date, 0L))));
        }
        return filled;
    }

    private List<CategoryCountResponse> categoryCounts(long communityId) {
        return repository.findCategoryCounts(communityId).stream()
                .map(row -> new CategoryCountResponse(
                        new CategoryResponse(
                                String.valueOf(row.getCategoryId()),
                                row.getCode(),
                                row.getName(),
                                row.getSortOrder()),
                        toInt(row.getItemCount())))
                .toList();
    }

    private List<PopularItemResponse> popularItems(long communityId, Instant fromAt, Instant toAt) {
        return repository.findPopularItems(communityId, fromAt, toAt, POPULAR_LIMIT).stream()
                .map(row -> new PopularItemResponse(
                        String.valueOf(row.getItemId()), row.getTitle(), toInt(row.getReturnedCount())))
                .toList();
    }

    /** 명세의 통계 수치는 int32다. 실제로 넘길 규모가 아니지만 넘치면 상한으로 막는다 */
    private int toInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
