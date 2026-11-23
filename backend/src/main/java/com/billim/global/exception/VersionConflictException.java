package com.billim.global.exception;

/**
 * expectedVersion 불일치. 응답에 currentVersion을 담는다.
 */
public class VersionConflictException extends BusinessException {

    private final long currentVersion;

    public VersionConflictException(long currentVersion) {
        super(ErrorCode.VERSION_CONFLICT);
        this.currentVersion = currentVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
