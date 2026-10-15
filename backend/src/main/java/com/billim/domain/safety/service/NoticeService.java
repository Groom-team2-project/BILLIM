package com.billim.domain.safety.service;

import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.safety.dto.CreateNoticeRequest;
import com.billim.domain.safety.dto.NoticePageResponse;
import com.billim.domain.safety.dto.NoticeResponse;
import com.billim.domain.safety.dto.UpdateNoticeRequest;
import com.billim.domain.safety.entity.Notice;
import com.billim.domain.safety.repository.NoticeRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * 커뮤니티 공지 (API 명세 14.1 ~ 14.6).
 *
 * <p>읽기는 그 동네의 유효 소속 회원 또는 ADMIN, 쓰기는 ADMIN만이다.
 * ADMIN 역할 확인은 컨트롤러가 먼저 하고, 여기서는 소속 기반 읽기 권한만 본다.
 */
@Service
@Transactional(readOnly = true)
public class NoticeService {

    private static final Logger log = LoggerFactory.getLogger(NoticeService.class);

    private final NoticeRepository noticeRepository;
    private final CommunityMembershipQuery membershipQuery;
    private final EntityManager entityManager;
    private final Clock clock;

    public NoticeService(NoticeRepository noticeRepository,
                         CommunityMembershipQuery membershipQuery,
                         EntityManager entityManager,
                         Clock clock) {
        this.noticeRepository = noticeRepository;
        this.membershipQuery = membershipQuery;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** E_063 동네 공지 목록. 유효 소속 회원 또는 ADMIN */
    public NoticePageResponse findCommunityNotices(long memberId, boolean admin, long communityId,
                                                    int page, int size) {
        if (!admin && !membershipQuery.hasValidMembership(memberId, communityId)) {
            throw new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
        }

        Page<Notice> notices = noticeRepository
                .findByCommunityIdAndDeletedAtIsNullOrderByPinnedDescCreatedAtDescIdDesc(
                        communityId, PageRequest.of(page, size));
        return toPage(notices, page, size);
    }

    /** E_064 공지 상세. 삭제된 공지는 404 */
    public NoticeResponse findNotice(long memberId, boolean admin, long noticeId) {
        Notice notice = requireVisible(noticeId);

        if (!admin && !membershipQuery.hasValidMembership(memberId, notice.getCommunityId())) {
            // 다른 동네 공지는 존재를 알리지 않는다 (명세 0.2)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return NoticeResponse.from(notice);
    }

    /** E_065 관리자 공지 목록. 동네 필터는 선택이다 */
    public NoticePageResponse findAllNotices(Long communityId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Notice> notices = communityId == null
                ? noticeRepository.findByDeletedAtIsNullOrderByPinnedDescCreatedAtDescIdDesc(pageRequest)
                : noticeRepository
                        .findByCommunityIdAndDeletedAtIsNullOrderByPinnedDescCreatedAtDescIdDesc(
                                communityId, pageRequest);
        return toPage(notices, page, size);
    }

    /** E_066 공지 작성. 활성 커뮤니티만 */
    @Transactional
    public NoticeResponse create(long adminId, CreateNoticeRequest request) {
        long communityId = request.communityIdAsLong();
        requireActiveCommunity(communityId);

        Notice notice = noticeRepository.save(Notice.of(
                communityId, adminId, request.title(), request.body(), request.pinned()));

        log.info("공지 작성: noticeId={} communityId={} adminId={}",
                notice.getId(), communityId, adminId);
        return NoticeResponse.from(notice);
    }

    /** E_067 공지 수정·고정. version 검사 후 전체 교체 */
    @Transactional
    public NoticeResponse update(long noticeId, UpdateNoticeRequest request) {
        Notice notice = lockVisible(noticeId);
        requireExpectedVersion(notice, request.expectedVersion());

        notice.update(request.title(), request.body(), request.pinned());

        // 응답의 version은 화면이 다음 수정에 그대로 쓴다. 커밋 전 값이 나가면 바로 충돌하므로
        // 여기서 flush해 증가된 version을 반영한다
        noticeRepository.flush();
        return NoticeResponse.from(notice);
    }

    /** E_068 공지 삭제. version 검사 후 논리 삭제 */
    @Transactional
    public void delete(long noticeId, long expectedVersion) {
        Notice notice = lockVisible(noticeId);
        requireExpectedVersion(notice, expectedVersion);

        notice.delete(clock.instant());
        log.info("공지 삭제: noticeId={}", noticeId);
    }

    private Notice requireVisible(long noticeId) {
        return noticeRepository.findByIdAndDeletedAtIsNull(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Notice lockVisible(long noticeId) {
        Notice notice = noticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (notice.isDeleted()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return notice;
    }

    /** 오래된 화면에서 보낸 수정·삭제를 거른다 */
    private void requireExpectedVersion(Notice notice, long expectedVersion) {
        if (notice.getVersion() != expectedVersion) {
            throw new VersionConflictException(notice.getVersion());
        }
    }

    /**
     * 비활성·없는 커뮤니티에는 공지를 쓸 수 없다.
     * 커뮤니티 도메인의 Repository를 바꾸지 않고 조회만 한다.
     */
    private void requireActiveCommunity(long communityId) {
        Long count = entityManager.createQuery("""
                        SELECT COUNT(c)
                          FROM Community c
                         WHERE c.id = :communityId
                           AND c.status = com.billim.domain.community.entity.CommunityStatus.ACTIVE
                        """, Long.class)
                .setParameter("communityId", communityId)
                .getSingleResult();

        if (count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private NoticePageResponse toPage(Page<Notice> notices, int page, int size) {
        List<NoticeResponse> items = notices.getContent().stream()
                .map(NoticeResponse::from)
                .toList();
        return new NoticePageResponse(items, page, size, notices.getTotalElements(), notices.hasNext());
    }
}
