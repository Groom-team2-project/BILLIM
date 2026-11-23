package com.billim.domain.safety.service;

import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.entity.NotificationTargetType;
import com.billim.domain.notification.service.DomainEvent;
import com.billim.domain.notification.service.DomainEventPublisher;
import com.billim.domain.notification.service.EventPayload;
import com.billim.domain.notification.service.RecipientRole;
import com.billim.domain.safety.dto.AdminReportPageResponse;
import com.billim.domain.safety.dto.AdminReportResponse;
import com.billim.domain.safety.dto.ResolveReportRequest;
import com.billim.domain.safety.entity.Report;
import com.billim.domain.safety.entity.ReportAction;
import com.billim.domain.safety.entity.ReportActionType;
import com.billim.domain.safety.entity.ReportStatus;
import com.billim.domain.safety.entity.Sanction;
import com.billim.domain.safety.repository.ReportActionRepository;
import com.billim.domain.safety.repository.ReportRepository;
import com.billim.domain.safety.repository.SanctionRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import com.billim.global.json.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 운영 신고 조회와 처리·제재 결정 (API 명세 13.1 ~ 13.3).
 * 호출 전에 ADMIN 역할을 확인하는 것은 컨트롤러의 몫이고, 여기서는 대상 자격만 다시 본다.
 */
@Service
@Transactional(readOnly = true)
public class AdminReportService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportService.class);

    private final ReportRepository reportRepository;
    private final ReportActionRepository reportActionRepository;
    private final SanctionRepository sanctionRepository;
    private final MemberRepository memberRepository;
    private final DomainEventPublisher eventPublisher;
    private final MemberRowLock memberRowLock;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    public AdminReportService(ReportRepository reportRepository,
                              ReportActionRepository reportActionRepository,
                              SanctionRepository sanctionRepository,
                              MemberRepository memberRepository,
                              DomainEventPublisher eventPublisher,
                              MemberRowLock memberRowLock,
                              JsonCodec jsonCodec,
                              Clock clock) {
        this.reportRepository = reportRepository;
        this.reportActionRepository = reportActionRepository;
        this.sanctionRepository = sanctionRepository;
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
        this.memberRowLock = memberRowLock;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    /** 운영 신고 목록. status·communityId는 선택 필터다 (API 명세 13.1) */
    public AdminReportPageResponse findReports(ReportStatus status, Long communityId,
                                               int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Report> reports = findPage(status, communityId, pageRequest);

        return new AdminReportPageResponse(
                toResponses(reports.getContent()),
                page, size, reports.getTotalElements(), reports.hasNext());
    }

    private Page<Report> findPage(ReportStatus status, Long communityId, PageRequest pageRequest) {
        if (communityId != null && status != null) {
            return reportRepository.findByCommunityIdAndStatusOrderByCreatedAtDescIdDesc(
                    communityId, status, pageRequest);
        }
        if (communityId != null) {
            return reportRepository.findByCommunityIdOrderByCreatedAtDescIdDesc(communityId, pageRequest);
        }
        if (status != null) {
            return reportRepository.findByStatusOrderByCreatedAtDescIdDesc(status, pageRequest);
        }
        return reportRepository.findAllByOrderByCreatedAtDescIdDesc(pageRequest);
    }

    /** 신고 증거와 처리 상세 (API 명세 13.2) */
    public AdminReportResponse findReport(long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return toResponse(report, reportActionRepository.findByReportId(reportId).orElse(null));
    }

    /**
     * 신고 처리·제재 결정 (API 명세 13.3).
     *
     * <p>처리 이력·제재·알림 사건을 한 트랜잭션에서 저장한다. 롤백되면 알림도 남지 않는다.
     * 잠금 순서는 대상 회원 → 신고로 고정해 동시 처리에서 교착이 생기지 않게 한다.
     */
    @Transactional
    public AdminReportResponse resolve(long adminId, long reportId, ResolveReportRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Member targetMember = memberRowLock.lock(report.getTargetMemberId());
        Report locked = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        ReportActionType action = request.action();
        requireSanctionable(adminId, targetMember, action);

        // 오래된 화면에서 보낸 처리를 거른다. 이미 처리된 신고는 version과 무관하게 409
        if (locked.getVersion() != request.expectedVersion()) {
            if (!locked.isPending()) {
                throw new BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED);
            }
            throw new VersionConflictException(locked.getVersion());
        }
        locked.resolve();

        ReportAction reportAction = reportActionRepository.save(ReportAction.of(
                reportId, adminId, action, request.publicReason(), request.internalNote()));

        Instant now = clock.instant();
        if (action.createsSanction()) {
            sanctionRepository.save(Sanction.from(
                    targetMember.getId(), reportAction.getId(), action, now));
        }

        publishResolved(locked, action);

        log.info("신고 처리: reportId={} adminId={} action={}", reportId, adminId, action);
        return toResponse(locked, reportAction);
    }

    /**
     * 관리자 자신에 대한 제재와 ADMIN 대상 제한은 금지한다 (API 명세 13.3).
     * 제재를 만들지 않는 조치(WARN·DISMISS)는 대상 자격을 따지지 않는다.
     */
    private void requireSanctionable(long adminId, Member targetMember, ReportActionType action) {
        if (!action.createsSanction()) {
            return;
        }
        if (targetMember.getId() == adminId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "자신에게 제재를 적용할 수 없습니다.");
        }
        if (targetMember.getRole() == MemberRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 회원은 제재할 수 없습니다.");
        }
    }

    /**
     * 처리 결과 알림 사건. 신고자는 신고 결과로, 제재 대상은 자기 회원 정보로 이동한다 (ERD 4.6).
     * 대상에게는 조치 라벨만 전달하고 관리자가 쓴 사유 원문과 신고자 정보는 담지 않는다.
     */
    private void publishResolved(Report report, ReportActionType action) {
        List<EventPayload.Recipient> recipients = new ArrayList<>();
        recipients.add(EventPayload.Recipient.of(report.getReporterId(), RecipientRole.REPORTER));
        if (action.notifiesTargetMember()) {
            recipients.add(EventPayload.Recipient.movingTo(
                    report.getTargetMemberId(), RecipientRole.SANCTIONED,
                    NotificationTargetType.MEMBER_SELF, report.getTargetMemberId()));
        }

        eventPublisher.publish(DomainEvent.of(
                EventType.REPORT_RESOLVED,
                report.getId(),
                report.getVersion(),
                EventPayload.forReport(report.getId(), recipients,
                        EventPayload.Display.ofLabel(action.label()))));
    }

    private AdminReportResponse toResponse(Report report, ReportAction action) {
        return AdminReportResponse.of(
                report,
                requireMember(report.getReporterId()),
                requireMember(report.getTargetMemberId()),
                action,
                jsonCodec.read(report.getTargetSnapshot(), ReportTarget.ReportSnapshot.class));
    }

    /** 목록 조립. 신고자·대상 회원과 처리 이력을 한 번에 모아 붙인다 */
    private List<AdminReportResponse> toResponses(List<Report> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }

        List<Long> memberIds = reports.stream()
                .flatMap(report -> List.of(report.getReporterId(), report.getTargetMemberId()).stream())
                .distinct()
                .toList();
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        Map<Long, ReportAction> actions = reportActionRepository.findByReportIdIn(
                        reports.stream().map(Report::getId).toList())
                .stream()
                .collect(Collectors.toMap(ReportAction::getReportId, Function.identity()));

        return reports.stream()
                .map(report -> AdminReportResponse.of(
                        report,
                        requireLoaded(members, report.getReporterId()),
                        requireLoaded(members, report.getTargetMemberId()),
                        actions.get(report.getId()),
                        jsonCodec.read(report.getTargetSnapshot(), ReportTarget.ReportSnapshot.class)))
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
            throw new IllegalStateException("신고 관련 회원을 찾을 수 없습니다: memberId=" + memberId);
        }
        return member;
    }
}
