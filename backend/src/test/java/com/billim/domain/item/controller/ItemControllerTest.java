package com.billim.domain.item.controller;

import com.billim.domain.item.dto.CategoryResponse;
import com.billim.domain.item.dto.ImageResponse;
import com.billim.domain.item.dto.ItemDetailResponse;
import com.billim.domain.item.dto.ItemPageResponse;
import com.billim.domain.item.dto.ItemSearchCondition;
import com.billim.domain.item.dto.ItemSort;
import com.billim.domain.item.dto.MemberSummaryResponse;
import com.billim.domain.item.dto.PlaceResponse;
import com.billim.domain.item.service.CategoryService;
import com.billim.domain.item.service.ItemService;
import com.billim.domain.item.service.MediaService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 물건 API 컨트롤러 테스트. 운영 Security 설정은 건드리지 않는다.
 * 로그인 회원은 spring-security-test(@WithMockUser, csrf())로 만든다. 컨트롤러는 인증 이름을 회원 ID로 읽는다(TODO(A): principal 형식 확정 시 갱신).
 * 401·403 계약은 운영 SecurityFilterChain이 아직 없으므로 Spring Boot 기본 보안 설정 기준으로 확인한다.
 */
// OAuth2 client-id/secret은 실제 환경변수 없이도 컨텍스트가 뜨도록 테스트 값을 주입 (BillimApplicationTests와 같은 방식)
@WebMvcTest(controllers = {ItemController.class, MediaController.class, CategoryController.class}, properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
})
@WithMockUser(username = "1")
class ItemControllerTest {

    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";
    private static final String CREATE_BODY = """
            {"title":"전동드릴","description":"설명","categoryId":"1","placeId":"10",
             "availableStartDate":"2026-10-05","availableEndDate":"2026-10-10","imageIds":["5","6"]}""";
    private static final String UPDATE_BODY = """
            {"title":"전동드릴","categoryId":"1","placeId":"10",
             "availableStartDate":"2026-10-05","availableEndDate":"2026-10-10","imageIds":["6","5"],"expectedVersion":0}""";

    @Autowired MockMvc mvc;
    @MockitoBean ItemService itemService;
    @MockitoBean MediaService mediaService;
    @MockitoBean CategoryService categoryService;

    private static ItemDetailResponse detail() {
        return new ItemDetailResponse("7", "전동드릴", "설명",
                new MemberSummaryResponse("1", "정우", Instant.parse("2026-03-01T00:00:00Z")), "100",
                new CategoryResponse("1", "TOOL", "공구", 0),
                new PlaceResponse("10", "100", "정문", BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), null),
                List.of(new ImageResponse("6", 0, "/api/v1/media/6/content"),
                        new ImageResponse("5", 1, "/api/v1/media/5/content")),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10), "PUBLIC", 120, "COMMUNITY_CENTER", 0L,
                List.of("EDIT", "HIDE", "DELETE"), Instant.parse("2026-10-02T00:00:00Z"));
    }

    // ───── 등록 ─────

    @Test
    @DisplayName("물건 등록에 성공하면 201과 상세를 돌려준다")
    void createReturns201WithDetail() throws Exception {
        when(itemService.create(anyLong(), any())).thenReturn(detail());

        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("7"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.description").value("설명"));
    }

    @Test
    @DisplayName("Idempotency Key 헤더가 없으면 400이다")
    void createWithoutIdempotencyKeyReturns400() throws Exception {
        mvc.perform(post("/api/v1/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(itemService);
    }

    @Test
    @DisplayName("Idempotency Key가 UUID가 아니면 400이다")
    void createWithNonUuidIdempotencyKeyReturns400() throws Exception {
        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(itemService);
    }

    @Test
    @DisplayName("제목이 비었으면 400과 필드 오류를 돌려준다")
    void createWithBlankTitleReturns400WithFieldError() throws Exception {
        String body = CREATE_BODY.replace("\"전동드릴\"", "\"\"");
        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    @DisplayName("제목이 100자를 넘으면 400이다")
    void createWithTooLongTitleReturns400() throws Exception {
        String body = CREATE_BODY.replace("전동드릴", "가".repeat(101));
        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("필수 필드가 빠지면 400이다")
    void createWithMissingRequiredFieldsReturns400() throws Exception {
        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"전동드릴\",\"imageIds\":[\"5\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("선언하지 않은 필드가 있으면 400이다")
    void createWithUnknownFieldReturns400() throws Exception {
        String body = CREATE_BODY.replace("\"title\"", "\"ownerId\":\"9\",\"title\"");
        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("이미지가 없으면 400이다")
    void createWithoutImagesReturns400() throws Exception {
        String body = CREATE_BODY.replace("[\"5\",\"6\"]", "[]");
        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("imageIds"));
    }

    @Test
    @DisplayName("이미지가 6개 이상이면 400이다")
    void createWithSixImagesReturns400() throws Exception {
        String body = CREATE_BODY.replace("[\"5\",\"6\"]", "[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]");
        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("imageIds"));
    }

    @Test
    @WithMockUser(username = "not-a-member-id")
    @DisplayName("회원 ID를 읽을 수 없는 인증 정보는 401 UNAUTHENTICATED다")
    void createWithUnreadableMemberIdReturns401() throws Exception {
        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verify(itemService, never()).create(anyLong(), any());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("로그인하지 않은 요청은 Security에서 막혀 서비스까지 오지 않는다")
    void anonymousRequestIsBlockedBySecurity() throws Exception {
        // 현재 계약: 운영 SecurityFilterChain(A파트)이 없어 Spring Boot 기본 보안(OAuth2 로그인 리다이렉트)이 응답한다.
        // TODO(A): 명세의 401 UNAUTHENTICATED 응답이 구현되면 status().isUnauthorized()로 좁힌다.
        mvc.perform(get("/api/v1/items/7"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(300));
        verifyNoInteractions(itemService);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("로그인하지 않은 XHR 요청은 막히고 브라우저 Basic 인증창을 띄우는 WWW-Authenticate 헤더가 없다")
    void anonymousXhrRequestIsBlockedWithoutBasicChallenge() throws Exception {
        // TODO(A): 명세의 401 UNAUTHENTICATED 응답이 구현되면 status().isUnauthorized()도 함께 검증한다.
        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", KEY)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(300))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        verifyNoInteractions(itemService);
    }

    @Test
    @DisplayName("CSRF 토큰 없이 변경 요청을 보내면 기존 Security 계약대로 403이다")
    void mutationWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/v1/items").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(itemService);
    }

    @Test
    @DisplayName("다른 동네 장소나 연결 불가 사진 오류를 서비스에서 받으면 그대로 돌려준다")
    void createPropagatesServiceErrorCode() throws Exception {
        when(itemService.create(anyLong(), any())).thenThrow(new BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE));

        mvc.perform(post("/api/v1/items").with(csrf()).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_ATTACHABLE"));
    }

    // ───── 검색 ─────

    @Test
    @DisplayName("검색 조건이 서비스로 전달되고 200이다")
    void searchPassesConditionsAndReturns200() throws Exception {
        when(itemService.search(anyLong(), any())).thenReturn(new ItemPageResponse(List.of(), 1, 10, 0, false));

        mvc.perform(get("/api/v1/items")
                        .param("keyword", "드릴").param("categoryId", "1").param("placeId", "10")
                        .param("startDate", "2026-10-05").param("endDate", "2026-10-07")
                        .param("sort", "NEAREST").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<ItemSearchCondition> cap = ArgumentCaptor.forClass(ItemSearchCondition.class);
        verify(itemService).search(eq(1L), cap.capture());
        ItemSearchCondition c = cap.getValue();
        assertThat(c.keyword()).isEqualTo("드릴");
        assertThat(c.categoryId()).isEqualTo(1L);
        assertThat(c.placeId()).isEqualTo(10L);
        assertThat(c.startDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(c.endDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(c.sort()).isEqualTo(ItemSort.NEAREST);
        assertThat(c.page()).isEqualTo(1);
        assertThat(c.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("검색 기본값은 첫 페이지 20개 최신순이다")
    void searchDefaultsToFirstPageOf20Latest() throws Exception {
        when(itemService.search(anyLong(), any())).thenReturn(new ItemPageResponse(List.of(), 0, 20, 0, false));

        mvc.perform(get("/api/v1/items")).andExpect(status().isOk());

        ArgumentCaptor<ItemSearchCondition> cap = ArgumentCaptor.forClass(ItemSearchCondition.class);
        verify(itemService).search(eq(1L), cap.capture());
        assertThat(cap.getValue().page()).isZero();
        assertThat(cap.getValue().size()).isEqualTo(20);
        assertThat(cap.getValue().sort()).isEqualTo(ItemSort.LATEST);
    }

    @Test
    @DisplayName("지원하지 않는 정렬이면 400이다")
    void searchWithUnsupportedSortReturns400() throws Exception {
        mvc.perform(get("/api/v1/items").param("sort", "PRICE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(itemService);
    }

    @Test
    @DisplayName("날짜 형식이 틀리면 400이다")
    void searchWithMalformedDateReturns400() throws Exception {
        mvc.perform(get("/api/v1/items").param("startDate", "2026/10/05").param("endDate", "2026-10-07"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("숫자가 아닌 page는 400이다")
    void searchWithNonNumericPageReturns400() throws Exception {
        mvc.perform(get("/api/v1/items").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ID 형식이 틀린 categoryId는 400이다")
    void searchWithMalformedCategoryIdReturns400() throws Exception {
        mvc.perform(get("/api/v1/items").param("categoryId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("검색 기간 검증 실패는 서비스의 400을 그대로 돌려준다")
    void searchPropagatesPeriodValidationError() throws Exception {
        when(itemService.search(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_REQUEST, "시작일과 종료일은 함께 입력해야 합니다."));

        mvc.perform(get("/api/v1/items").param("startDate", "2026-10-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ───── 상세 ─────

    @Test
    @DisplayName("상세 조회에 성공하면 200이다")
    void getReturns200() throws Exception {
        when(itemService.get(1L, 7L)).thenReturn(detail());

        mvc.perform(get("/api/v1/items/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("7"))
                .andExpect(jsonPath("$.distanceBasis").value("COMMUNITY_CENTER"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 물건이면 404다")
    void getMissingItemReturns404() throws Exception {
        when(itemService.get(1L, 999L)).thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(get("/api/v1/items/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("물건 ID가 숫자가 아니면 400이다")
    void getWithNonNumericIdReturns400() throws Exception {
        mvc.perform(get("/api/v1/items/abc")).andExpect(status().isBadRequest());
    }

    // ───── 수정 ─────

    @Test
    @DisplayName("물건 수정에 성공하면 200이다")
    void updateReturns200() throws Exception {
        when(itemService.update(anyLong(), anyLong(), any())).thenReturn(detail());

        mvc.perform(put("/api/v1/items/7").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("7"));
    }

    @Test
    @DisplayName("수정에 expectedVersion이 없으면 400이다")
    void updateWithoutExpectedVersionReturns400() throws Exception {
        String body = UPDATE_BODY.replace(",\"expectedVersion\":0", "");
        mvc.perform(put("/api/v1/items/7").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("expectedVersion"));
    }

    @Test
    @DisplayName("다른 회원 물건을 수정하면 403이다")
    void updateOthersItemReturns403() throws Exception {
        when(itemService.update(anyLong(), anyLong(), any())).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mvc.perform(put("/api/v1/items/7").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("버전이 다르면 409와 currentVersion을 돌려준다")
    void updateVersionConflictReturns409WithCurrentVersion() throws Exception {
        when(itemService.update(anyLong(), anyLong(), any())).thenThrow(new VersionConflictException(3L));

        mvc.perform(put("/api/v1/items/7").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(3));
    }

    // ───── 공개 상태 ─────

    @Test
    @DisplayName("공개 상태 변경에 성공하면 200이다")
    void changeVisibilityReturns200() throws Exception {
        when(itemService.changeVisibility(anyLong(), anyLong(), any())).thenReturn(detail());

        mvc.perform(patch("/api/v1/items/7/visibility").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"visibility\":\"HIDDEN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개 상태에 DELETED를 보내도 서비스는 PUBLIC HIDDEN만 받는다")
    void changeVisibilityToDeletedReturns400() throws Exception {
        when(itemService.changeVisibility(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        mvc.perform(patch("/api/v1/items/7/visibility").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"visibility\":\"DELETED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("알 수 없는 공개 상태 값은 400이다")
    void changeVisibilityWithUnknownValueReturns400() throws Exception {
        mvc.perform(patch("/api/v1/items/7/visibility").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"visibility\":\"SECRET\"}"))
                .andExpect(status().isBadRequest());
    }

    // ───── 삭제 ─────

    @Test
    @DisplayName("물건 삭제에 성공하면 204이고 본문이 없다")
    void deleteReturns204WithoutBody() throws Exception {
        mvc.perform(delete("/api/v1/items/7").with(csrf()).param("expectedVersion", "0"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(itemService).delete(1L, 7L, 0L);
    }

    @Test
    @DisplayName("삭제에 expectedVersion이 없으면 400이다")
    void deleteWithoutExpectedVersionReturns400() throws Exception {
        mvc.perform(delete("/api/v1/items/7").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(itemService);
    }

    @Test
    @DisplayName("삭제 expectedVersion이 음수면 400이다")
    void deleteWithNegativeExpectedVersionReturns400() throws Exception {
        mvc.perform(delete("/api/v1/items/7").with(csrf()).param("expectedVersion", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 물건을 삭제하면 404다")
    void deleteMissingItemReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(itemService).delete(anyLong(), anyLong(), anyLong());

        mvc.perform(delete("/api/v1/items/999").with(csrf()).param("expectedVersion", "0"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("다른 회원 물건을 삭제하면 403이다")
    void deleteOthersItemReturns403() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(itemService).delete(anyLong(), anyLong(), anyLong());

        mvc.perform(delete("/api/v1/items/7").with(csrf()).param("expectedVersion", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("진행 중 대여가 있으면 409다")
    void deleteWithOpenRentalsReturns409() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.ITEM_HAS_OPEN_RENTALS))
                .when(itemService).delete(anyLong(), anyLong(), anyLong());

        mvc.perform(delete("/api/v1/items/7").with(csrf()).param("expectedVersion", "0"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_HAS_OPEN_RENTALS"));
    }
}
