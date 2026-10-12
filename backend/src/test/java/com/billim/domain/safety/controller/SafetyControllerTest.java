package com.billim.domain.safety.controller;

import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.safety.dto.AdminReportPageResponse;
import com.billim.domain.safety.dto.AdminReportResponse;
import com.billim.domain.safety.dto.ReportPageResponse;
import com.billim.domain.safety.dto.ReportResponse;
import com.billim.domain.safety.dto.SanctionPageResponse;
import com.billim.domain.safety.dto.SanctionResponse;
import com.billim.domain.safety.entity.ReportActionType;
import com.billim.domain.safety.entity.ReportStatus;
import com.billim.domain.safety.entity.ReportTargetType;
import com.billim.domain.safety.service.AdminReportService;
import com.billim.domain.safety.service.ReportService;
import com.billim.domain.safety.service.SanctionService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 신고·운영 API 계약 (API 명세 12.4 ~ 13.5). 운영 Security 설정은 건드리지 않는다.
 * 이 슬라이스에는 운영 SecurityFilterChain이 없으므로 ADMIN 제한은 컨트롤러의 역할 검사로 확인한다.
 * (경로 단위 제한은 SecurityConfig의 /api/v1/admin/** 규칙이 담당한다)
 */
@WebMvcTest(controllers = {
        ReportController.class, AdminReportController.class, AdminSanctionController.class
}, properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
})
class SafetyControllerTest {

    private static final long ME = 7L;
    private static final long ADMIN = 1L;
    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";

    @Autowired MockMvc mockMvc;
    @MockitoBean ReportService reportService;
    @MockitoBean AdminReportService adminReportService;
    @MockitoBean SanctionService sanctionService;

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

    private static ReportResponse report(String action, String publicReason) {
        return new ReportResponse("301", "ITEM", "55",
                new MemberSummaryResponse("9", "정우", Instant.parse("2026-03-01T00:00:00Z")),
                "MONEY_REQUEST", null, action == null ? "PENDING" : "RESOLVED",
                publicReason, action, 0L, Instant.parse("2026-10-12T01:00:00Z"));
    }

    // ---------- 신고 접수·조회 ----------

    @Test
    @DisplayName("신고 접수는 201이고 ID를 문자열로 내린다")
    void createReportReturnsCreated() throws Exception {
        when(reportService.create(anyLong(), any())).thenReturn(report(null, null));

        mockMvc.perform(post("/api/v1/reports")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"ITEM","targetId":"55","reasonCode":"MONEY_REQUEST"}""")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("301"))
                .andExpect(jsonPath("$.targetId").value("55"))
                .andExpect(jsonPath("$.targetMember.id").value("9"))
                // 처리 전에는 조치 정보를 생략한다
                .andExpect(jsonPath("$.action").doesNotExist())
                .andExpect(jsonPath("$.publicReason").doesNotExist());
    }

    @Test
    @DisplayName("요청 본문의 대상·사유를 그대로 서비스에 전달한다")
    void createReportPassesRequest() throws Exception {
        when(reportService.create(anyLong(), any())).thenReturn(report(null, null));

        mockMvc.perform(post("/api/v1/reports")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"MESSAGE","targetId":"77","reasonCode":"OTHER","detail":"욕설"}""")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(com.billim.domain.safety.dto.CreateReportRequest.class);
        verify(reportService).create(org.mockito.ArgumentMatchers.eq(ME), captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo(ReportTargetType.MESSAGE);
        assertThat(captor.getValue().targetIdAsLong()).isEqualTo(77L);
        assertThat(captor.getValue().detail()).isEqualTo("욕설");
    }

    @Test
    @DisplayName("Idempotency-Key가 UUID가 아니면 400이다")
    void createReportRequiresUuidIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"ITEM","targetId":"55","reasonCode":"ABUSE"}""")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @Test
    @DisplayName("알 수 없는 사유 코드나 선언하지 않은 필드는 400이다")
    void createReportRejectsInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"ITEM","targetId":"55","reasonCode":"NOPE"}""")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/reports")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"ITEM","targetId":"55","reasonCode":"ABUSE","extra":1}""")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @Test
    @DisplayName("내 신고 목록은 기본 페이지 크기를 쓰고 최대 50을 넘지 못한다")
    void myReportsUseDefaultPaging() throws Exception {
        when(reportService.findMyReports(ME, 0, 20))
                .thenReturn(new ReportPageResponse(List.of(report(null, null)), 0, 20, 1, false));

        mockMvc.perform(get("/api/v1/members/me/reports").with(authentication(user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("301"))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/v1/members/me/reports")
                        .param("size", "51").with(authentication(user())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내 신고 상세는 처리 결과를 담아 내려준다")
    void myReportDetailIncludesAction() throws Exception {
        when(reportService.findMyReport(ME, 301L)).thenReturn(report("WARN", "대여료를 요구했습니다"));

        mockMvc.perform(get("/api/v1/reports/301").with(authentication(user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.action").value("WARN"))
                .andExpect(jsonPath("$.publicReason").value("대여료를 요구했습니다"));
    }

    // ---------- 운영 ----------

    @Test
    @DisplayName("관리자 신고 목록은 상태·동네 필터를 전달한다")
    void adminListPassesFilters() throws Exception {
        when(adminReportService.findReports(any(), anyLong(), anyInt(), anyInt()))
                .thenReturn(new AdminReportPageResponse(List.of(), 0, 20, 0, false));

        mockMvc.perform(get("/api/v1/admin/reports")
                        .param("status", "PENDING")
                        .param("communityId", "100")
                        .with(authentication(admin())))
                .andExpect(status().isOk());

        verify(adminReportService).findReports(ReportStatus.PENDING, 100L, 0, 20);
    }

    @Test
    @DisplayName("관리자 상세에는 신고자와 내부 메모가 담긴다")
    void adminDetailExposesReporterAndInternalNote() throws Exception {
        when(adminReportService.findReport(301L)).thenReturn(new AdminReportResponse(
                report("WARN", "대여료를 요구했습니다"),
                new MemberSummaryResponse("7", "소빈", Instant.parse("2026-04-01T00:00:00Z")),
                "100", "계좌로 보내주세요", "증거 캡처 보관"));

        mockMvc.perform(get("/api/v1/admin/reports/301").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporter.displayName").value("소빈"))
                .andExpect(jsonPath("$.internalNote").value("증거 캡처 보관"))
                .andExpect(jsonPath("$.evidenceText").value("계좌로 보내주세요"))
                .andExpect(jsonPath("$.report.id").value("301"));
    }

    @Test
    @DisplayName("신고 처리는 expectedVersion과 조치를 그대로 전달한다")
    void resolvePassesRequest() throws Exception {
        when(adminReportService.resolve(anyLong(), anyLong(), any())).thenReturn(new AdminReportResponse(
                report("SUSPEND_7D", "대여료를 요구했습니다"),
                new MemberSummaryResponse("7", "소빈", Instant.parse("2026-04-01T00:00:00Z")),
                "100", null, null));

        mockMvc.perform(post("/api/v1/admin/reports/301/resolve")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"action":"SUSPEND_7D",
                                 "publicReason":"대여료를 요구했습니다","internalNote":"증거 보관"}""")
                        .with(authentication(admin())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.action").value("SUSPEND_7D"));

        var captor = ArgumentCaptor.forClass(com.billim.domain.safety.dto.ResolveReportRequest.class);
        verify(adminReportService).resolve(
                org.mockito.ArgumentMatchers.eq(ADMIN),
                org.mockito.ArgumentMatchers.eq(301L),
                captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(ReportActionType.SUSPEND_7D);
        assertThat(captor.getValue().expectedVersion()).isZero();
    }

    @Test
    @DisplayName("처리 사유가 비면 400이고 처리하지 않는다")
    void resolveRequiresPublicReason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reports/301/resolve")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"action":"WARN","publicReason":"  "}""")
                        .with(authentication(admin())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(adminReportService);
    }

    @Test
    @DisplayName("일반 회원은 운영 API를 호출할 수 없다")
    void normalMemberCannotCallAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports").with(authentication(user())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/sanctions").with(authentication(user())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/reports/301/resolve")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"action":"BAN","publicReason":"사유"}""")
                        .with(authentication(user())).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminReportService);
        verifyNoInteractions(sanctionService);
    }

    @Test
    @DisplayName("제재 이력은 회원 필터를 전달하고 isEffective를 내린다")
    void sanctionListPassesMemberFilter() throws Exception {
        when(sanctionService.findSanctions(9L, 0, 20)).thenReturn(new SanctionPageResponse(
                List.of(new SanctionResponse("11", "9", "SUSPEND",
                        Instant.parse("2026-10-12T01:00:00Z"), Instant.parse("2026-10-19T01:00:00Z"),
                        null, true)),
                0, 20, 1, false));

        mockMvc.perform(get("/api/v1/admin/sanctions")
                        .param("memberId", "9").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("11"))
                .andExpect(jsonPath("$.items[0].memberId").value("9"))
                .andExpect(jsonPath("$.items[0].isEffective").value(true))
                .andExpect(jsonPath("$.items[0].revokedAt").doesNotExist());
    }

    @Test
    @DisplayName("제재 해제는 사유가 필수다")
    void revokeRequiresReason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sanctions/11/revoke")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}")
                        .with(authentication(admin())).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sanctionService);
    }

    @Test
    @DisplayName("제재 해제는 해제된 제재를 돌려준다")
    void revokeReturnsSanction() throws Exception {
        when(sanctionService.revoke(ADMIN, 11L, "오인 제재")).thenReturn(new SanctionResponse(
                "11", "9", "BAN", Instant.parse("2026-10-12T01:00:00Z"), null,
                Instant.parse("2026-10-12T02:00:00Z"), false));

        mockMvc.perform(post("/api/v1/admin/sanctions/11/revoke")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"오인 제재\"}")
                        .with(authentication(admin())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isEffective").value(false))
                .andExpect(jsonPath("$.revokedAt").value("2026-10-12T02:00:00Z"));
    }
}
