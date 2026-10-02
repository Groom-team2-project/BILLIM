package com.billim.domain.item.controller;

import com.billim.domain.item.dto.CategoryListResponse;
import com.billim.domain.item.dto.CategoryResponse;
import com.billim.domain.item.dto.MediaFileResponse;
import com.billim.domain.item.port.CurrentMemberProvider;
import com.billim.domain.item.service.CategoryService;
import com.billim.domain.item.service.ItemService;
import com.billim.domain.item.service.MediaService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 사진·카테고리 API 컨트롤러 테스트 (B_013~B_016). 인증은 Mock/spring-security-test로 처리 */
// OAuth2 client-id/secret은 실제 환경변수 없이도 컨텍스트가 뜨도록 테스트 값을 주입 (BillimApplicationTests와 같은 방식)
@WebMvcTest(controllers = {ItemController.class, MediaController.class, CategoryController.class}, properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
})
@WithMockUser
class MediaAndCategoryControllerTest {

    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";

    @Autowired MockMvc mvc;
    @MockitoBean ItemService itemService;
    @MockitoBean MediaService mediaService;
    @MockitoBean CategoryService categoryService;
    @MockitoBean CurrentMemberProvider currentMember;

    @BeforeEach
    void setUp() {
        when(currentMember.requireMemberId()).thenReturn(1L);
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg", bytes);
    }

    @Test
    @DisplayName("카테고리 목록은 200으로 items를 돌려준다")
    void categoriesReturn200WithItems() throws Exception {
        when(categoryService.list()).thenReturn(new CategoryListResponse(List.of(
                new CategoryResponse("1", "TOOL", "공구", 0), new CategoryResponse("2", "CAMP", "캠핑", 1))));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value("TOOL"))
                .andExpect(jsonPath("$.items[0].sortOrder").value(0));
    }

    @Test
    @DisplayName("인증되지 않으면 카테고리 조회는 401이다")
    void categoriesWhenMemberUnresolvedReturn401() throws Exception {
        when(currentMember.requireMemberId()).thenThrow(new BusinessException(ErrorCode.UNAUTHENTICATED));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(categoryService);
    }

    @Test
    @DisplayName("사진 업로드에 성공하면 201이다")
    void uploadReturns201() throws Exception {
        when(mediaService.upload(anyLong(), any(), any())).thenReturn(new MediaFileResponse(
                "5", "image/jpeg", 100, 10, 10, "/api/v1/media/5/content", "TEMP", Instant.parse("2026-10-03T00:00:00Z")));

        mvc.perform(multipart("/api/v1/media").file(file(new byte[]{1, 2, 3})).with(csrf())
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("5"))
                .andExpect(jsonPath("$.status").value("TEMP"))
                .andExpect(jsonPath("$.contentUrl").value("/api/v1/media/5/content"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
        verify(mediaService).upload(eq(1L), any(), eq("image/jpeg"));
    }

    @Test
    @DisplayName("사진 업로드에 Idempotency Key가 없으면 400이다")
    void uploadWithoutIdempotencyKeyReturns400() throws Exception {
        mvc.perform(multipart("/api/v1/media").file(file(new byte[]{1})).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(mediaService);
    }

    @Test
    @DisplayName("사진 업로드에 file 파트가 없으면 400이다")
    void uploadWithoutFilePartReturns400() throws Exception {
        mvc.perform(multipart("/api/v1/media").with(csrf()).header("Idempotency-Key", KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미지 검증 실패는 명세의 상태 코드로 돌려준다")
    void uploadImageValidationErrorUsesSpecStatus() throws Exception {
        when(mediaService.upload(anyLong(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.UNSUPPORTED_IMAGE));

        mvc.perform(multipart("/api/v1/media").file(file(new byte[]{1})).with(csrf()).header("Idempotency-Key", KEY))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
    }

    @Test
    @DisplayName("사진 읽기는 이미지 바이너리와 private 캐시 헤더를 돌려준다")
    void contentReturnsBinaryWithPrivateCache() throws Exception {
        when(mediaService.read(1L, 5L)).thenReturn(new MediaService.MediaContent(new byte[]{1, 2, 3}, "image/png"));

        mvc.perform(get("/api/v1/media/5/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("볼 수 없는 사진은 404다")
    void contentNotVisibleReturns404() throws Exception {
        when(mediaService.read(anyLong(), anyLong())).thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(get("/api/v1/media/5/content")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("미연결 사진 삭제는 204다")
    void deleteTempMediaReturns204() throws Exception {
        mvc.perform(delete("/api/v1/media/5").with(csrf())).andExpect(status().isNoContent());
        verify(mediaService).deleteTemp(1L, 5L);
    }

    @Test
    @DisplayName("연결된 사진을 삭제하면 409 MEDIA IN USE다")
    void deleteAttachedMediaReturns409() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.MEDIA_IN_USE))
                .when(mediaService).deleteTemp(anyLong(), anyLong());

        mvc.perform(delete("/api/v1/media/5").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEDIA_IN_USE"));
    }
}
