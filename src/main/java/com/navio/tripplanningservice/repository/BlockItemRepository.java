package com.navio.tripplanningservice.repository;

import com.navio.tripplanningservice.model.BlockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlockItemRepository extends JpaRepository<BlockItem, UUID> {

    List<BlockItem> findByBlockIdOrderByDisplayOrder(UUID blockId);

    Optional<BlockItem> findByIdAndBlockId(UUID id, UUID blockId);

    void deleteByBlockId(UUID blockId);
}
