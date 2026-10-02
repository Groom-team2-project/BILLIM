package com.billim.domain.item.service;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * 업로드 이미지 검사·재인코딩. 파일명·확장자는 보지 않고
 * 매직 바이트 → 선언 MIME 일치 → 실제 디코딩 순으로 확인한다.
 * 재인코딩으로 EXIF 등 메타데이터를 제거한다. (API 명세 B_014)
 */
@Component
public class ImageInspector {

    public static final long MAX_BYTES = 10L * 1024 * 1024;   // 10 MiB. spring.servlet.multipart.max-file-size와 같은 값
    public static final long MAX_PIXELS = 40_000_000L;         // 40MP 디코딩 한도

    private static final float JPEG_QUALITY = 0.9f;

    public record Result(byte[] bytes, String mimeType, String extension, int width, int height) {
    }

    private enum Format {
        JPEG("image/jpeg", "jpeg", "jpg"),
        PNG("image/png", "png", "png"),
        WEBP("image/webp", "webp", "webp");

        final String mime;
        final String readerName;
        final String ext;

        Format(String mime, String readerName, String ext) {
            this.mime = mime;
            this.readerName = readerName;
            this.ext = ext;
        }
    }

    /**
     * @param declaredMime 요청에 선언된 Content-Type
     * @throws BusinessException FILE_TOO_LARGE(413) · UNSUPPORTED_IMAGE(415) · INVALID_IMAGE(422) · INVALID_REQUEST(빈 파일)
     */
    public Result inspect(byte[] data, String declaredMime) {
        if (data == null || data.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "빈 파일은 올릴 수 없습니다.");
        }
        if (data.length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        Format format = detect(data);
        if (format == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
        }
        // 선언 MIME과 실제 내용이 다르면 거부 (확장자 위조·MIME 위조)
        if (declaredMime == null || !normalize(declaredMime).equals(format.mime)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
        }

        BufferedImage image = decode(data, format);
        return reencode(image, format);
    }

    private static String normalize(String mime) {
        int semi = mime.indexOf(';');
        String m = (semi >= 0 ? mime.substring(0, semi) : mime).trim().toLowerCase(Locale.ROOT);
        return m.equals("image/jpg") ? "image/jpeg" : m;
    }

    /** 매직 바이트로 형식 판별. GIF·TXT 등은 null */
    private static Format detect(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return Format.JPEG;
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (startsWith(d, png)) {
            return Format.PNG;
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return Format.WEBP;
        }
        return null;
    }

    private static boolean startsWith(byte[] d, byte[] prefix) {
        if (d.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (d[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** 헤더 크기로 픽셀 한도를 먼저 확인한 뒤 전체 디코딩. 실패하면 INVALID_IMAGE */
    private static BufferedImage decode(byte[] data, Format format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.readerName);
        if (!readers.hasNext()) {
            // WebP 디코더(imageio-webp)가 없으면 서버 설정 문제
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
        }
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(in, true, true);
            long w = reader.getWidth(0);
            long h = reader.getHeight(0);
            if (w < 1 || h < 1 || w * h > MAX_PIXELS) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE);
            }
            BufferedImage image = reader.read(0);
            if (image == null) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE);
            }
            return image;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        } finally {
            reader.dispose();
        }
    }

    /**
     * 메타데이터 제거를 위해 다시 인코딩한다.
     * JPEG→JPEG, PNG→PNG. WebP는 쓰기 인코더가 없어 알파가 없으면 JPEG, 있으면 PNG로 저장한다.
     */
    private static Result reencode(BufferedImage image, Format source) {
        boolean alpha = image.getColorModel().hasAlpha();
        Format target = switch (source) {
            case JPEG -> Format.JPEG;
            case PNG -> Format.PNG;
            case WEBP -> alpha ? Format.PNG : Format.JPEG;
        };
        try {
            byte[] out = target == Format.JPEG ? writeJpeg(toRgb(image)) : writePng(image);
            if (out.length > MAX_BYTES) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            }
            return new Result(out, target.mime, target.ext, image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB || src.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static byte[] writeJpeg(BufferedImage rgb) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bos)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(out);
            writer.write(null, new IIOImage(rgb, null, null), param);   // 메타데이터 null: EXIF 제거
            out.flush();
            return bos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static byte[] writePng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", bos)) {
                throw new IOException("png writer 없음");
            }
            return bos.toByteArray();
        }
    }
}
