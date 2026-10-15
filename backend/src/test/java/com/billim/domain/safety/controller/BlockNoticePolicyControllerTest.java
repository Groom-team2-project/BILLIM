package com.billim.domain.safety.controller;

import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.safety.dto.BlockPageResponse;
import com.billim.domain.safety.dto.BlockResponse;
import com.billim.domain.safety.dto.NoticePageResponse;
import com.billim.domain.safety.dto.NoticeResponse;
import com.billim.domain.safety.dto.PolicyPageResponse;
import com.billim.domain.safety.service.BlockService;
import com.billim.domain.safety.service.NoticeService;
import com.billim.domain.safety.service.PolicyService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 차단·공지·정책 API 계약 (API 명세 12.1~12.3, 14.1~14.7) */
@WebMvcTest(controllers = {
        BlockController.class, NoticeController.class, AdminNoticeController.class, PolicyController.class
}, properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
})
class BlockNoticePolicyControllerTest {

    private static final long ME = 7L;
    private static final long ADMIN = 1L;
    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";

    @Autowired MockMvc mockMvc;
    @MockitoBean BlockService blockService;
    @MockitoBean NoticeService noticeService;
    @MockitoBean PolicyService policyService;

    private static Authentication login(long memberId, MemberRole role) {
        LoginMember member = new LoginMember(memberId, role);
        return new OAuth2AuthenticationToken(member, member.getAuthorities(), "kakao");
    }

    private static Authentication user() {
        return login(ME, MemberRole.USER);
    }

    private static Authentication admin() {
        return login(ADMIN, MemberRole.ADMIN);
    }

    private static NoticeResponse notice() {
        return new NoticeResponse("55", "100", "정전 안내", "본문", true, 2L,
                Instant.parse("2026-10-13T01:00:00Z"));
    }

    // ---------- 차단 ----------

    @Test
    @DisplayName("차단 목록은 대상 회원 요약과 차단 시각을 내린다")
    void blockListReturnsMemberSummary() throws Exception {
        when(blockService.findMyBlocks(ME, 0, 20)).thenReturn(new BlockPageResponse(
                List.of(new BlockResponse(
                        new MemberSummaryResponse("9", "정우", Instant.parse("2026-03-01T00:00:00Z")),
                        Instant.parse("2026-10-13T01:00:00Z"))),
                0, 20, 1, false));

        mockMvc.perform(get("/api/v1/members/me/blocks").with(authentication(user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].member.id").value("9"))
                .andExpect(jsonPath("$.items[0].member.displayName").value("정우"))
                .andExpect(jsonPath("$.items[0].blockedAt").value("2026-10-13T01:00:00Z"));
    }

    @Test
    @DisplayName("차단은 204이고 본문이 없다")
    void blockReturnsNoContent() throws Exception {
        mockMvc.perform(put("/api/v1/members/me/blocks/9")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isNoContent());

        verify(blockService).block(ME, 9L);
    }

    @Test
    @DisplayName("차단 해제는 204이고 없는 관계도 성공이다")
    void unblockReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me/blocks/9")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isNoContent());

        verify(blockService).unblock(ME, 9L);
    }

    @Test
    @DisplayName("알 수 없는 회원 차단은 404로 전달한다")
    void blockingUnknownMemberIsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(blockService).block(anyLong(), anyLong());

        mockMvc.perform(put("/api/v1/members/me/blocks/9")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---------- 공지 ----------

    @Test
    @DisplayName("동네 공지 목록은 회원과 관리자 여부를 서비스에 전달한다")
    void communityNoticesPassAdminFlag() throws Exception {
        when(noticeService.findCommunityNotices(anyLong(), org.mockito.ArgumentMatchers.anyBoolean(),
                anyLong(), anyInt(), anyInt()))
                .thenReturn(new NoticePageResponse(List.of(notice()), 0, 20, 1, false));

        mockMvc.perform(get("/api/v1/communities/100/notices").with(authentication(user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("55"))
                .andExpect(jsonPath("$.items[0].communityId").value("100"))
                .andExpect(jsonPath("$.items[0].pinned").value(true));

        verify(noticeService).findCommunityNotices(ME, false, 100L, 0, 20);
    }

    @Test
    @DisplayName("관리자가 조회하면 관리자 플래그가 올라간다")
    void adminFlagIsPassedForAdmin() throws Exception {
        when(noticeService.findNotice(anyLong(), org.mockito.ArgumentMatchers.anyBoolean(), anyLong()))
                .thenReturn(notice());

        mockMvc.perform(get("/api/v1/notices/55").with(authentication(admin())))
                .andExpect(status().isOk());

        verify(noticeService).findNotice(ADMIN, true, 55L);
    }

    @Test
    @DisplayName("소속이 없으면 403 COMMUNITY_VERIFICATION_REQUIRED로 전달한다")
    void noticesRequireMembership() throws Exception {
        when(noticeService.findCommunityNotices(anyLong(), org.mockito.ArgumentMatchers.anyBoolean(),
                anyLong(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED));

        mockMvc.perform(get("/api/v1/communities/100/notices").with(authentication(user())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMUNITY_VERIFICATION_REQUIRED"));
    }

    @Test
    @DisplayName("공지 작성은 201이고 요청 값을 그대로 전달한다")
    void createNoticeReturnsCreated() throws Exception {
        when(noticeService.create(anyLong(), any())).thenReturn(notice());

        mockMvc.perform(post("/api/v1/admin/notices")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"communityId":"100","title":"정전 안내","body":"본문","pinned":true}""")
                        .with(authentication(admin())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("55"));

        var captor = ArgumentCaptor.forClass(com.billim.domain.safety.dto.CreateNoticeRequest.class);
        verify(noticeService).create(eq(ADMIN), captor.capture());
        assertThat(captor.getValue().communityIdAsLong()).isEqualTo(100L);
        assertThat(captor.getValue().pinned()).isTrue();
    }

    @Test
    @DisplayName("제목·본문이 비면 400이고 작성하지 않는다")
    void createNoticeRejectsBlankFields() throws Exception {
        mockMvc.perform(post("/api/v1/admin/notices")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"communityId":"100","title":"  ","body":"본문","pinned":false}""")
                        .with(authentication(admin())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(noticeService);
    }

    @Test
    @DisplayName("공지 수정은 expectedVersion을 전달한다")
    void updateNoticePassesVersion() throws Exception {
        when(noticeService.update(anyLong(), any())).thenReturn(notice());

        mockMvc.perform(put("/api/v1/admin/notices/55")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1,"title":"정전 안내","body":"본문","pinned":true}""")
                        .with(authentication(admin())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        var captor = ArgumentCaptor.forClass(com.billim.domain.safety.dto.UpdateNoticeRequest.class);
        verify(noticeService).update(eq(55L), captor.capture());
        assertThat(captor.getValue().expectedVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("공지 삭제는 expectedVersion 질의가 필수이고 204다")
    void deleteNoticeRequiresVersion() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/notices/55")
                        .param("expectedVersion", "2")
                        .with(authentication(admin())).with(csrf()))
                .andExpect(status().isNoContent());
        verify(noticeService).delete(55L, 2L);

        mockMvc.perform(delete("/api/v1/admin/notices/55")
                        .with(authentication(admin())).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("일반 회원은 공지 관리 API를 호출할 수 없다")
    void normalMemberCannotManageNotices() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notices").with(authentication(user())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/notices")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"communityId":"100","title":"제목","body":"본문","pinned":false}""")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/notices/55")
                        .param("expectedVersion", "2")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(noticeService);
    }

    // ---------- 정책 ----------

    @Test
    @DisplayName("정책 문서는 키·제목·본문·버전을 내린다")
    void policyIsServed() throws Exception {
        when(policyService.find("community")).thenReturn(new PolicyPageResponse(
                "community", "커뮤니티 규칙", "본문", "2026-10-13"));

        mockMvc.perform(get("/api/v1/policies/community").with(authentication(user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("community"))
                .andExpect(jsonPath("$.title").value("커뮤니티 규칙"))
                .andExpect(jsonPath("$.versionLabel").value("2026-10-13"));
    }

    @Test
    @DisplayName("작성되지 않은 문서는 404다")
    void unwrittenPolicyIsNotFound() throws Exception {
        when(policyService.find("terms"))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/policies/terms").with(authentication(user())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("키 형식이 다르면 400이고 조회하지 않는다")
    void malformedPolicyKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/policies/Community!").with(authentication(user())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(policyService);
    }
}
