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

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM trip.trip WHERE destination_country_code IS NULL
              AND id > :after ORDER BY id LIMIT :batchSize
            """, nativeQuery = true)
    java.util.List<Trip> findUnresolvedAfter(UUID after, int batchSize);

    Page<Trip> findByUserId(UUID userId, Pageable pageable);

    Page<Trip> findByUserIdAndVisibility(UUID userId, TripVisibility visibility, Pageable pageable);

    Optional<Trip> findByIdAndUserId(UUID id, UUID userId);

    // Scalar read bypasses an already-loaded Trip in the persistence context.
    @org.springframework.data.jpa.repository.Query("select t.version from Trip t where t.id = :id and t.userId = :userId")
    Optional<Long> findCurrentVersion(UUID id, UUID userId);
}
