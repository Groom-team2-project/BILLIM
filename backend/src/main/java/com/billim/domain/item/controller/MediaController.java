package com.billim.domain.item.controller;

import com.billim.domain.item.dto.MediaFileResponse;
import com.billim.domain.item.service.IdParser;
import com.billim.domain.item.service.MediaService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** B_014 업로드 · B_015 읽기 · B_016 미연결 삭제 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaFileResponse upload(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                    @RequestPart("file") MultipartFile file,
                                    Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        IdempotencyKeys.require(idempotencyKey);
        try {
            return mediaService.upload(memberId, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "파일을 읽을 수 없습니다.");
        }
    }

    @GetMapping("/{mediaId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String mediaId, Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        MediaService.MediaContent c = mediaService.read(memberId, IdParser.parse(mediaId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.mimeType()))
                .cacheControl(CacheControl.noCache().cachePrivate())   // 권한이 바뀔 수 있어 매번 재검증. 공개 캐시 금지
                .header("X-Content-Type-Options", "nosniff")
                .body(c.bytes());
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String mediaId, Authentication authentication) {
        long memberId = AuthenticatedMember.id(authentication);
        mediaService.deleteTemp(memberId, IdParser.parse(mediaId));
    }
}
