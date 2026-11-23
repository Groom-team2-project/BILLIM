package com.billim.domain.item.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMediaStorageTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("저장 읽기 삭제")
    void savesReadsAndDeletes() throws IOException {
        LocalMediaStorage storage = new LocalMediaStorage(root.toString());
        storage.save("2026/10/a.jpg", new byte[]{1, 2, 3});
        assertThat(storage.read("2026/10/a.jpg")).containsExactly(1, 2, 3);
        storage.delete("2026/10/a.jpg");
        assertThat(Files.exists(root.resolve("2026/10/a.jpg"))).isFalse();
        storage.delete("2026/10/a.jpg");   // 없는 파일 삭제는 무시
    }

    @Test
    @DisplayName("저장 후 임시 파일이 남지 않는다")
    void leavesNoTempFile() throws IOException {
        LocalMediaStorage storage = new LocalMediaStorage(root.toString());
        storage.save("2026/10/b.png", new byte[]{9});
        try (var files = Files.list(root.resolve("2026/10"))) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("b.png");
        }
    }

    @Test
    @DisplayName("루트 밖으로 나가는 경로는 거부한다")
    void rejectsPathOutsideRoot() {
        LocalMediaStorage storage = new LocalMediaStorage(root.toString());
        assertThatThrownBy(() -> storage.save("../evil.jpg", new byte[]{1})).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> storage.read("../../etc/passwd")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> storage.save("/abs/evil.jpg", new byte[]{1})).isInstanceOf(IOException.class);
    }
}
