package com.navio.tripplanningservice.repository;

import com.navio.tripplanningservice.model.ListBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListBlockRepository extends JpaRepository<ListBlock, UUID> {

    List<ListBlock> findByTripIdOrderByDisplayOrder(UUID tripId);

    Optional<ListBlock> findByIdAndTripId(UUID id, UUID tripId);

    Optional<ListBlock> findByTripIdAndClientId(UUID tripId, String clientId);

    void deleteByTripId(UUID tripId);
}
