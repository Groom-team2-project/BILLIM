package com.billim.domain.rental.controller;

import com.billim.domain.rental.service.AppointmentService;
import com.billim.domain.rental.service.AppointmentCommandService;
import com.billim.domain.rental.dto.*;
import com.billim.domain.rental.entity.AppointmentKind;
import com.billim.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppointmentControllerTest {
    private final AppointmentCommandService commands = mock(AppointmentCommandService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AppointmentController(mock(AppointmentService.class), commands))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test @DisplayName("제안 201과 수락·철회 200 및 슬롯 버전 전달")
    void routesCommands() throws Exception {
        mvc.perform(post("/api/v1/rentals/10/appointments/PICKUP/proposals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"scheduledAt\":\"2026-10-15T10:00:00Z\",\"placeId\":\"1\"}"))
                .andExpect(status().isCreated());
        verify(commands).propose(eq(10L), eq(AppointmentKind.PICKUP), any(), isNull());
        for (String action : new String[]{"accept", "withdraw"}) {
            mvc.perform(post("/api/v1/appointments/20/" + action).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedVersion\":1}")).andExpect(status().isOk());
        }
        verify(commands).accept(eq(20L), eq(new AppointmentVersionRequest(1L)), isNull());
        verify(commands).withdraw(eq(20L), eq(new AppointmentVersionRequest(1L)), isNull());
    }

    @Test @DisplayName("잘못된 kind·음수 버전·누락 시각·숫자 범위 밖 장소 거부")
    void rejectsInvalidInput() throws Exception {
        for (String body : new String[]{
                "{\"expectedVersion\":-1,\"scheduledAt\":\"2026-10-15T10:00:00Z\",\"placeId\":\"1\"}",
                "{\"expectedVersion\":0,\"placeId\":\"1\"}",
                "{\"expectedVersion\":0,\"scheduledAt\":\"2026-10-15T10:00:00Z\",\"placeId\":\"9223372036854775808\"}"}) {
            mvc.perform(post("/api/v1/rentals/10/appointments/PICKUP/proposals")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(commands);
    }
}
