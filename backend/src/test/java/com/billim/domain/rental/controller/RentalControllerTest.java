package com.billim.domain.rental.controller;

import com.billim.global.exception.GlobalExceptionHandler;
import com.billim.domain.rental.service.RentalService;
import com.billim.domain.rental.service.RentalQueryService;
import com.billim.domain.rental.dto.*;
import com.billim.domain.rental.dto.CreateRentalRequest;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RentalControllerTest {

    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";
    private MockMvc mvc;
    private RentalService service;
    private RentalQueryService queries;

    @BeforeEach
    void setUp() {
        service = mock(RentalService.class);
        queries = mock(RentalQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RentalController(service, queries), new RentalAvailabilityController(queries))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("대여 생성 요청을 서비스에 전달하고 201 반환")
    void createCallsService() throws Exception {
        mvc.perform(post("/api/v1/rentals").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"1","startDate":"2026-10-10","endDate":"2026-10-11"}
                                """))
                .andExpect(status().isCreated());
        verify(service).createRental(eq(KEY), eq(new CreateRentalRequest("1",
                java.time.LocalDate.of(2026, 10, 10), java.time.LocalDate.of(2026, 10, 11))), isNull());
    }

    @Test
    @DisplayName("필수 저장 기반 미연동 시 503 오류 응답")
    void createReturnsDependencyUnavailable() throws Exception {
        when(service.createRental(anyString(), any(), any())).thenThrow(
                new com.billim.global.exception.BusinessException(
                        com.billim.global.exception.ErrorCode.DEPENDENCY_UNAVAILABLE));
        mvc.perform(post("/api/v1/rentals").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"1","startDate":"2026-10-10","endDate":"2026-10-11"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "handover", "return", "reject", "cancel"})
    @DisplayName("각 상태 변경 경로의 서비스 전달 및 성공 응답")
    void commandsCallServices(String action) throws Exception {
        String body = action.equals("reject") || action.equals("cancel")
                ? "{\"expectedVersion\":0,\"reason\":\"일정 변경\"}"
                : "{\"expectedVersion\":0}";
        mvc.perform(post("/api/v1/rentals/1/" + action).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        switch (action) {
            case "approve" -> verify(service).approveRental(eq(1L), eq(KEY), eq(new RentalVersionRequest(0L)), isNull());
            case "reject" -> verify(service).rejectRental(eq(1L), eq(KEY), eq(new RejectRentalRequest(0L, "일정 변경")), isNull());
            case "cancel" -> verify(service).cancelRental(eq(1L), eq(KEY), eq(new CancelRentalRequest(0L, "일정 변경")), isNull());
            case "handover" -> verify(service).handoverRental(eq(1L), eq(KEY), eq(new RentalVersionRequest(0L)), isNull());
            case "return" -> verify(service).returnRental(eq(1L), eq(KEY), eq(new RentalVersionRequest(0L)), isNull());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/rentals?role=BORROWER", "/api/v1/rentals?role=OWNER",
            "/api/v1/rentals/summary", "/api/v1/rentals/1"})
    @DisplayName("목록·건수·상세 조회 경로 연결")
    void queriesCallServices(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reject", "cancel"})
    @DisplayName("거절·취소 사유 누락·공백·길이 초과 거부")
    void reasonIsRequired(String action) throws Exception {
        for (String body : new String[]{"{\"expectedVersion\":0}",
                "{\"expectedVersion\":0,\"reason\":\"   \"}",
                "{\"expectedVersion\":0,\"reason\":\"" + "가".repeat(501) + "\"}"}) {
            mvc.perform(post("/api/v1/rentals/1/" + action).header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"expectedVersion\":-1}"})
    @DisplayName("버전 누락 또는 음수 거부")
    void invalidVersionReturnsBadRequest(String body) throws Exception {
        mvc.perform(post("/api/v1/rentals/1/approve").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("멱등 키 누락·잘못된 형식 거부")
    void invalidIdempotencyKeyReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/rentals/1/approve")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/rentals/1/approve").header("Idempotency-Key", "invalid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?role=OTHER", "?role=OWNER&status=UNKNOWN", "?role=OWNER&page=-1",
            "?role=OWNER&size=0", "?role=OWNER&size=51", "?role=OWNER&itemId=0"})
    @DisplayName("목록 조회 역할·상태·페이지·물건 ID 검증")
    void invalidQueryReturnsBadRequest(String query) throws Exception {
        mvc.perform(get("/api/v1/rentals" + query)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    @DisplayName("잘못된 거래 ID 거부")
    void invalidRentalIdReturnsBadRequest(String id) throws Exception {
        mvc.perform(get("/api/v1/rentals/" + id)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("생성 요청의 필수 필드와 ID 상한 검증")
    void invalidCreateReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/rentals").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/rentals").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"9223372036854775808","startDate":"2026-10-10","endDate":"2026-10-11"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("승인 충돌은 거래 ID, 버전 충돌은 현재 버전 응답")
    void commandConflictsExposeSpecifiedFields() throws Exception {
        when(service.approveRental(anyLong(), anyString(), any(), any())).thenThrow(
                new com.billim.global.exception.RentalDateConflictException(java.util.List.of(12L)));
        mvc.perform(post("/api/v1/rentals/1/approve").header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.conflictingRentalIds[0]").value("12"));
        doThrow(new com.billim.global.exception.VersionConflictException(2))
                .when(service).approveRental(anyLong(), anyString(), any(), any());
        mvc.perform(post("/api/v1/rentals/1/approve").header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.currentVersion").value(2));
    }

    @Test
    @DisplayName("달력 조회 경로·필수 날짜 검증")
    void availabilityCallsService() throws Exception {
        mvc.perform(get("/api/v1/items/1/availability?from=2026-10-01&to=2026-10-31"))
                .andExpect(status().isOk());
        verify(queries).getAvailability(eq(1L), eq(java.time.LocalDate.parse("2026-10-01")),
                eq(java.time.LocalDate.parse("2026-10-31")), isNull(), isNull(), isNull());
        mvc.perform(get("/api/v1/items/1/availability?from=2026-10-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items/1/availability?from=bad&to=2026-10-31"))
                .andExpect(status().isBadRequest());
    }
}
