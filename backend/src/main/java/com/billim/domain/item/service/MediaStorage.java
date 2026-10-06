package com.billim.domain.item.service;

import java.io.IOException;

/** 사진 저장소. 서버 영속 볼륨만 사용한다 (S3 등 외부 스토리지 금지). */
public interface MediaStorage {

    /** 상대 키로 원자적 저장 */
    void save(String storageKey, byte[] bytes) throws IOException;

    byte[] read(String storageKey) throws IOException;

    /** 없는 파일은 무시 */
    void delete(String storageKey) throws IOException;
}
