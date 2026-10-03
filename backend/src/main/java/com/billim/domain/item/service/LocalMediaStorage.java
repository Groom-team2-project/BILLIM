package com.billim.domain.item.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 로컬 볼륨 저장소. 키는 서버가 만든 상대 경로이며 루트 밖으로 나가는 경로는 거부한다.
 * 임시 파일에 쓴 뒤 rename으로 원자적 반영.
 */
@Component
public class LocalMediaStorage implements MediaStorage {

    private final Path root;

    public LocalMediaStorage(@Value("${billim.media.storage-path:./data/media}") String storagePath) {
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @Override
    public void save(String storageKey, byte[] bytes) throws IOException {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public byte[] read(String storageKey) throws IOException {
        return Files.readAllBytes(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) throws IOException {
        Path p = root.resolve(storageKey).normalize();
        if (!p.startsWith(root) || p.equals(root)) {
            throw new IOException("허용되지 않는 저장 경로");
        }
        return p;
    }
}
