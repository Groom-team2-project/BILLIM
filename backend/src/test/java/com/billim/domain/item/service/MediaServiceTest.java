package com.billim.domain.item.service;

import com.billim.domain.item.dto.MediaFileResponse;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.MediaFile;
import com.billim.domain.item.entity.MediaStatus;
import com.billim.domain.item.repository.ItemImageRepository;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.item.repository.MediaFileRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 사진 업로드·읽기 권한·미연결 삭제 (API 명세 B_014~B_016). 실제 로컬 저장소(@TempDir)를 사용한다. */
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final long ME = 1L;
    private static final long OTHER = 2L;

    @Mock MediaFileRepository mediaFileRepository;
    @Mock ItemImageRepository itemImageRepository;
    @Mock ItemRepository itemRepository;
    @Mock ItemAccessPolicy accessPolicy;
    @TempDir Path root;

    LocalMediaStorage storage;
    MediaService service;

    @BeforeEach
    void setUp() {
        storage = new LocalMediaStorage(root.toString());
        service = new MediaService(mediaFileRepository, itemImageRepository, itemRepository, new ImageInspector(),
                storage, accessPolicy, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static byte[] jpg() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private long storedFileCount() throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private MediaFile stored(long id, long uploader, String key, byte[] bytes) throws IOException {
        storage.save(key, bytes);
        MediaFile m = MediaFile.temp(uploader, key, "image/jpeg", bytes.length, 40, 30, NOW);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    // ───── 업로드 ─────

    @Test
    @DisplayName("업로드하면 재인코딩한 파일을 저장하고 TEMP 메타데이터를 기록한다")
    void uploadStoresFileAndRecordsTempMetadata() throws IOException {
        when(mediaFileRepository.saveAndFlush(any())).thenAnswer(inv -> {
            MediaFile m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 5L);
            return m;
        });

        MediaFileResponse res = service.upload(ME, jpg(), "image/jpeg");

        ArgumentCaptor<MediaFile> captor = ArgumentCaptor.forClass(MediaFile.class);
        verify(mediaFileRepository).saveAndFlush(captor.capture());
        MediaFile saved = captor.getValue();
        assertThat(saved.getUploaderId()).isEqualTo(ME);
        assertThat(saved.getStatus()).isEqualTo(MediaStatus.TEMP);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusSeconds(24 * 3600));
        assertThat(saved.getStorageKey()).startsWith("2026/10/").endsWith(".jpg");
        assertThat(Files.readAllBytes(root.resolve(saved.getStorageKey()))).hasSize((int) saved.getByteSize());

        assertThat(res.id()).isEqualTo("5");
        assertThat(res.contentUrl()).isEqualTo("/api/v1/media/5/content");
        assertThat(res.width()).isEqualTo(40);
        assertThat(res.height()).isEqualTo(30);
    }

    @Test
    @DisplayName("검사에 실패한 파일은 저장하지 않는다")
    void invalidUploadStoresNothing() throws IOException {
        assertCode(() -> service.upload(ME, "not image".getBytes(), "image/jpeg"), ErrorCode.UNSUPPORTED_IMAGE);
        verify(mediaFileRepository, never()).saveAndFlush(any());
        assertThat(storedFileCount()).isZero();
    }

    @Test
    @DisplayName("메타데이터 기록에 실패하면 방금 저장한 파일을 지운다")
    void failedMetadataRemovesStoredFile() throws IOException {
        when(mediaFileRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        byte[] data = jpg();

        assertThatThrownBy(() -> service.upload(ME, data, "image/jpeg"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(storedFileCount()).isZero();
    }

    // ───── 읽기 권한 ─────

    @Test
    @DisplayName("TEMP 사진은 업로더만 읽고 다른 회원에게는 404다")
    void tempMediaIsReadableOnlyByUploader() throws IOException {
        byte[] bytes = jpg();
        MediaFile m = stored(5, ME, "2026/10/a.jpg", bytes);
        when(mediaFileRepository.findById(5L)).thenReturn(Optional.of(m));

        assertThat(service.read(ME, 5).bytes()).isEqualTo(bytes);
        assertCode(() -> service.read(OTHER, 5), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("연결된 사진은 물건 조회 권한이 있는 회원만 읽을 수 있다 (기존 대여 당사자 예외는 TODO(C))")
    void attachedMediaFollowsItemPermission() throws IOException {
        MediaFile m = stored(5, OTHER, "2026/10/b.jpg", jpg());
        m.attach(OTHER, NOW);
        Item item = Item.create(OTHER, 100L, 1L, 10L, "물건", null,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), NOW);
        when(mediaFileRepository.findById(5L)).thenReturn(Optional.of(m));
        when(itemImageRepository.findItemIdByMediaFileId(5L)).thenReturn(Optional.of(7L));
        when(itemRepository.findById(7L)).thenReturn(Optional.of(item));

        when(accessPolicy.canView(item, ME)).thenReturn(true);
        assertThat(service.read(ME, 5).mimeType()).isEqualTo("image/jpeg");

        when(accessPolicy.canView(item, ME)).thenReturn(false);
        assertCode(() -> service.read(ME, 5), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제 표시된 사진은 404다")
    void deletedMediaIsNotFound() throws IOException {
        MediaFile m = stored(5, ME, "2026/10/c.jpg", jpg());
        m.deleteTemp(NOW);
        when(mediaFileRepository.findById(5L)).thenReturn(Optional.of(m));
        assertCode(() -> service.read(ME, 5), ErrorCode.RESOURCE_NOT_FOUND);
    }

    // ───── 미연결 삭제 ─────

    @Test
    @DisplayName("자신의 TEMP 사진을 지우면 DELETED가 되고 파일이 정리된다")
    void deleteOwnTempMarksDeletedAndRemovesFile() throws IOException {
        MediaFile m = stored(5, ME, "2026/10/d.jpg", jpg());
        when(mediaFileRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(m));

        service.deleteTemp(ME, 5);

        assertThat(m.getStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(Files.exists(root.resolve("2026/10/d.jpg"))).isFalse();
    }

    @Test
    @DisplayName("다른 회원의 사진은 404, 연결된 사진은 409 MEDIA_IN_USE다")
    void deleteRejectsOthersAndAttached() throws IOException {
        MediaFile others = stored(5, OTHER, "2026/10/e.jpg", jpg());
        when(mediaFileRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(others));
        assertCode(() -> service.deleteTemp(ME, 5), ErrorCode.RESOURCE_NOT_FOUND);

        MediaFile attached = stored(6, ME, "2026/10/f.jpg", jpg());
        attached.attach(ME, NOW);
        when(mediaFileRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(attached));
        assertCode(() -> service.deleteTemp(ME, 6), ErrorCode.MEDIA_IN_USE);
        assertThat(Files.exists(root.resolve("2026/10/f.jpg"))).isTrue();
    }
}
