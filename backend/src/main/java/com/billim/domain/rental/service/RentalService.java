package com.billim.domain.rental.service;

import com.billim.domain.rental.dto.*;
import com.billim.global.security.LoginMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RentalService {

    @Transactional
    public RentalDetailStatusResponse createRental(
        String idempotencyKey,
        CreateRentalRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }

    @Transactional
    public RentalDetailStatusResponse cancelRental(
        Long rentalId,
        String idempotencyKey,
        CancelRentalRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }

    @Transactional
    public RentalDetailStatusResponse approveRental(
        Long rentalId,
        String idempotencyKey,
        RentalVersionRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }

    @Transactional
    public RentalDetailStatusResponse rejectRental(
        Long rentalId,
        String idempotencyKey,
        RejectRentalRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }

    @Transactional
    public RentalDetailStatusResponse handoverRental(
        Long rentalId,
        String idempotencyKey,
        RentalVersionRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }

    @Transactional
    public RentalDetailStatusResponse returnRental(
        Long rentalId,
        String idempotencyKey,
        RentalVersionRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }
}
