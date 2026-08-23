package com.navio.tripplanningservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "list_block", schema = "trip")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tripId;

    @Column(nullable = false, length = 160)
    private String clientId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ListBlockType type;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column
    private String blockColor;  // Hex color code for visual customization (e.g., #FF6B6B, #4ECDC4)

    @Column(nullable = false)
    private LocalDate blockDate;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (clientId == null) {
            clientId = UUID.randomUUID().toString();
        }
        if (blockDate == null) {
            blockDate = LocalDate.now();
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum ListBlockType {
        LIST,
        ITINERARY,      // Day-by-day or time-based activities
        CHECKLIST,      // Pre-trip or post-trip checklist
        NOTES,          // General notes about the trip
        PACKING,        // What to pack
        BUDGET,         // Budget and expense tracking
        ACCOMMODATIONS  // Hotel, lodging info
    }
}
