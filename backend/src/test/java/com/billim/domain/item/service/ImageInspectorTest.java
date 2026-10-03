package com.billim.domain.item.service;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 파일명·확장자가 아니라 실제 내용(매직 바이트·디코딩)으로 판정하는지 검증 */
class ImageInspectorTest {

    private final ImageInspector inspector = new ImageInspector();

    private static byte[] image(String format, int w, int h, int type) throws IOException {
        BufferedImage b = new BufferedImage(w, h, type);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(b, format, out);
        return out.toByteArray();
    }

    private static void assertCode(byte[] data, String mime, ErrorCode code) {
        assertThatThrownBy(() -> new ImageInspector().inspect(data, mime))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("정상 JPG")
    void acceptsJpg() throws IOException {
        ImageInspector.Result r = inspector.inspect(image("jpg", 30, 20, BufferedImage.TYPE_INT_RGB), "image/jpeg");
        assertThat(r.mimeType()).isEqualTo("image/jpeg");
        assertThat(r.width()).isEqualTo(30);
        assertThat(r.height()).isEqualTo(20);
    }

    @Test
    @DisplayName("정상 JPEG image jpg 선언도 허용한다")
    void acceptsJpegDeclaredAsImageJpg() throws IOException {
        // .jpg와 .jpeg는 같은 JPEG. 브라우저·도구가 image/jpg로 보내는 경우 포함
        ImageInspector.Result r = inspector.inspect(image("jpeg", 10, 10, BufferedImage.TYPE_INT_RGB), "image/jpg");
        assertThat(r.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("정상 PNG")
    void acceptsPng() throws IOException {
        ImageInspector.Result r = inspector.inspect(image("png", 30, 20, BufferedImage.TYPE_INT_ARGB), "image/png");
        assertThat(r.mimeType()).isEqualTo("image/png");
        assertThat(r.extension()).isEqualTo("png");
    }

    @Test
    @DisplayName("재인코딩된 결과는 다시 디코딩된다")
    void reencodedBytesDecode() throws IOException {
        ImageInspector.Result r = inspector.inspect(image("png", 8, 8, BufferedImage.TYPE_INT_RGB), "image/png");
        assertThat(ImageIO.read(new ByteArrayInputStream(r.bytes()))).isNotNull();
    }

    @Test
    @DisplayName("정상 WebP")
    void acceptsWebp() {
        // 1x1 WebP. 디코딩에는 imageio-webp 의존성이 필요하다.
        byte[] webp = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
        ImageInspector.Result r = inspector.inspect(webp, "image/webp");
        assertThat(r.width()).isEqualTo(1);
        assertThat(r.mimeType()).isIn("image/jpeg", "image/png");   // WebP는 쓰기 인코더가 없어 JPEG/PNG로 재저장
    }

    @Test
    @DisplayName("10MiB 초과는 413이다")
    void rejectsOver10MiBWith413() {
        byte[] over = new byte[10 * 1024 * 1024 + 1];
        new Random(1).nextBytes(over);
        over[0] = (byte) 0xFF;
        over[1] = (byte) 0xD8;
        over[2] = (byte) 0xFF;
        assertCode(over, "image/jpeg", ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("내용은 TXT인데 확장자와 MIME만 JPG이면 거부한다")
    void rejectsTextDisguisedAsJpg() {
        byte[] fake = "이건 텍스트입니다".getBytes(StandardCharsets.UTF_8);
        assertCode(fake, "image/jpeg", ErrorCode.UNSUPPORTED_IMAGE);
    }

    @Test
    @DisplayName("손상된 PNG는 422이다")
    void rejectsCorruptedPngWith422() throws IOException {
        byte[] png = image("png", 30, 20, BufferedImage.TYPE_INT_ARGB);
        assertCode(Arrays.copyOf(png, png.length / 2), "image/png", ErrorCode.INVALID_IMAGE);
    }

    @Test
    @DisplayName("손상된 JPEG는 422이다")
    void rejectsCorruptedJpegWith422() throws IOException {
        byte[] jpg = image("jpg", 30, 20, BufferedImage.TYPE_INT_RGB);
        assertCode(Arrays.copyOf(jpg, 10), "image/jpeg", ErrorCode.INVALID_IMAGE);
    }

    @Test
    @DisplayName("GIF는 지원하지 않는다")
    void rejectsGif() throws IOException {
        byte[] gif = image("gif", 5, 5, BufferedImage.TYPE_INT_RGB);
        assertCode(gif, "image/gif", ErrorCode.UNSUPPORTED_IMAGE);
        assertCode(gif, "image/jpeg", ErrorCode.UNSUPPORTED_IMAGE);   // GIF를 JPEG로 위장
    }

    @Test
    @DisplayName("TXT 등 이미지가 아닌 파일은 거부한다")
    void rejectsNonImageFile() {
        assertCode("plain text".getBytes(StandardCharsets.UTF_8), "text/plain", ErrorCode.UNSUPPORTED_IMAGE);
    }

    @Test
    @DisplayName("빈 파일은 400이다")
    void rejectsEmptyFileWith400() {
        assertCode(new byte[0], "image/png", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("MIME과 실제 내용이 다르면 거부한다")
    void rejectsMimeContentMismatch() throws IOException {
        byte[] png = image("png", 10, 10, BufferedImage.TYPE_INT_RGB);
        assertCode(png, "image/jpeg", ErrorCode.UNSUPPORTED_IMAGE);
    }

    @Test
    @DisplayName("40MP를 넘는 이미지는 디코딩 전에 422로 거부한다")
    void rejectsImagesOver40Megapixels() throws IOException {
        byte[] png = image("png", 1, 1, BufferedImage.TYPE_INT_RGB);
        // IHDR의 너비·높이(오프셋 16·20)를 10000x5000으로 조작 — 헤더만 보고 거부해야 한다
        ByteBuffer.wrap(png, 16, 8).putInt(10_000).putInt(5_000);
        assertCode(png, "image/png", ErrorCode.INVALID_IMAGE);
    }

    @Test
    @DisplayName("MIME이 없으면 거부한다")
    void rejectsMissingMime() throws IOException {
        assertCode(image("jpg", 10, 10, BufferedImage.TYPE_INT_RGB), null, ErrorCode.UNSUPPORTED_IMAGE);
    }
}
