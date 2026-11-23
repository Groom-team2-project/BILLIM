package com.billim.domain.rental.service;

/** 멱등 기록에 경로·대상·본문을 함께 전달하는 내부 명령. */
public record RentalCommand(Action action, long rentalId, long expectedVersion, String reason) {
    public enum Action { APPROVE, REJECT, CANCEL, HANDOVER, RETURN }
}
