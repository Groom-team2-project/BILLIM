package com.billim.domain.rental.repository;

import com.billim.domain.rental.entity.Rental;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalRepository extends JpaRepository<Rental, Long> {

}
