package com.billim.domain.safety.service;

import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.safety.dto.CreateReportRequest;
import com.billim.domain.safety.dto.ReportPageResponse;
import com.billim.domain.safety.dto.ReportResponse;
import com.billim.domain.safety.entity.Report;
import com.billim.domain.safety.entity.ReportAction;
import com.billim.domain.safety.repository.ReportActionRepository;
import com.billim.domain.safety.repository.ReportRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.json.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 신고 접수와 내 신고 조회 (API 명세 12.4 ~ 12.6).
 * 신고자 본인 경로만 제공한다. 관리자 경로는 {@link AdminReportService}가 맡는다.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final ReportActionRepository reportActionRepository;
    private final MemberRepository memberRepository;
    private final ReportTargetResolver targetResolver;
    private final MemberRowLock memberRowLock;
    private final JsonCodec jsonCodec;

    public ReportService(ReportRepository reportRepository,
                         ReportActionRepository reportActionRepository,
                         MemberRepository memberRepository,
                         ReportTargetResolver targetResolver,
                         MemberRowLock memberRowLock,
                         JsonCodec jsonCodec) {
        this.reportRepository = reportRepository;
        this.reportActionRepository = reportActionRepository;
        this.memberRepository = memberRepository;
        this.targetResolver = targetResolver;
        this.memberRowLock = memberRowLock;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 신고 접수. 같은 대상·사유의 미처리 신고가 있으면 거부한다.
     *
     * <p>중복 검사와 저장 사이에 다른 요청이 끼어들지 않도록 신고자 행을 잠근다.
     * 한 회원의 접수를 직렬화하는 것이라 다른 회원의 신고는 막지 않는다. (ERD 4.7)
     */
    @Transactional
    public ReportResponse create(long reporterId, CreateReportRequest request) {
        Member reporter = memberRowLock.lock(reporterId);

        ReportTarget target = targetResolver.resolve(
                reporter, request.targetType(), request.targetIdAsLong());

        boolean duplicate = reportRepository.existsPendingDuplicate(
                reporterId, request.targetType(), target.targetMemberId(),
                request.reasonCode(), target.targetRef());
        if (duplicate) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
        }

        Report report = reportRepository.save(Report.open(
                reporterId,
                request.targetType(),
                target.targetMemberId(),
                target.targetRef(),
                target.communityId(),
                request.reasonCode(),
                request.detail(),
                jsonCodec.write(target.snapshot())));

        log.info("신고 접수: reportId={} targetType={} reasonCode={}",
                report.getId(), report.getTargetType(), report.getReasonCode());

        return ReportResponse.of(report, requireMember(target.targetMemberId()), null);
    }

    /** 내 접수 내역. 신고자 정보·internalNote는 담지 않는다 (API 명세 12.5) */
    public ReportPageResponse findMyReports(long reporterId, int page, int size) {
        Page<Report> reports = reportRepository.findByReporterIdOrderByCreatedAtDescIdDesc(
                reporterId, PageRequest.of(page, size));

        List<ReportResponse> items = toResponses(reports.getContent());
        return new ReportPageResponse(items, page, size, reports.getTotalElements(), reports.hasNext());
    }

    /** 내 신고 결과 상세. 신고자만 조회한다 (API 명세 12.6) */
    public ReportResponse findMyReport(long reporterId, long reportId) {
        Report report = reportRepository.findById(reportId)
                .filter(found -> found.isReportedBy(reporterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return ReportResponse.of(
                report,
                requireMember(report.getTargetMemberId()),
                reportActionRepository.findByReportId(reportId).orElse(null));
    }

    /** 목록 응답 조립. 대상 회원과 처리 이력을 건별로 조회하지 않고 한 번에 모아 붙인다 */
    private List<ReportResponse> toResponses(List<Report> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }

        Map<Long, Member> members = memberRepository.findAllById(
                        reports.stream().map(Report::getTargetMemberId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        Map<Long, ReportAction> actions = reportActionRepository.findByReportIdIn(
                        reports.stream().map(Report::getId).toList())
                .stream()
                .collect(Collectors.toMap(ReportAction::getReportId, Function.identity()));

        return reports.stream()
                .map(report -> ReportResponse.of(
                        report,
                        requireLoaded(members, report.getTargetMemberId()),
                        actions.get(report.getId())))
                .toList();
    }

    private Member requireMember(long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 신고 행의 FK가 가리키는 회원은 반드시 있어야 한다. 없으면 데이터 손상이므로 숨기지 않는다 */
    private static Member requireLoaded(Map<Long, Member> members, long memberId) {
        Member member = members.get(memberId);
        if (member == null) {
            throw new IllegalStateException("신고 대상 회원을 찾을 수 없습니다: memberId=" + memberId);
        }
        return member;
    }
}
