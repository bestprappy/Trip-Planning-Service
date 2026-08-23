package com.navio.tripplanningservice.repository;

import com.navio.tripplanningservice.model.ChecklistSubItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChecklistSubItemRepository extends JpaRepository<ChecklistSubItem, UUID> {

    List<ChecklistSubItem> findByItemIdOrderByDisplayOrder(UUID itemId);

    void deleteByItemId(UUID itemId);
}
