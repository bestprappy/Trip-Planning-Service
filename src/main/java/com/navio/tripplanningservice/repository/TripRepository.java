package com.navio.tripplanningservice.repository;

import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.model.TripVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    Page<Trip> findByUserId(UUID userId, Pageable pageable);

    Page<Trip> findByUserIdAndVisibility(UUID userId, TripVisibility visibility, Pageable pageable);

    Optional<Trip> findByIdAndUserId(UUID id, UUID userId);
}
