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

    @Column(length = 512)
    private String destinationId;
    @Column
    private String destinationName;
    @Column
    private Double destinationLat;
    @Column
    private Double destinationLng;
    @Column
    private String destinationCountry;

    /**
     * Where this day starts, when the traveller overrode the default.
     *
     * <p>Normally null: a day starts where the previous one ended, and that is
     * derived rather than stored so the two can never drift apart. It is set on
     * the first day (the trip origin) and on any day the traveller sets out from
     * somewhere other than last night's stop.
     */
    @Column(length = 512)
    private String startAnchorId;
    @Column(length = 20)
    private String startAnchorKind;
    @Column
    private String startAnchorName;
    @Column(length = 512)
    private String startAnchorAddress;
    @Column
    private Double startAnchorLat;
    @Column
    private Double startAnchorLng;

    /** Where this day ends — typically the night's accommodation. */
    @Column(length = 512)
    private String endAnchorId;
    @Column(length = 20)
    private String endAnchorKind;
    @Column
    private String endAnchorName;
    @Column(length = 512)
    private String endAnchorAddress;
    @Column
    private Double endAnchorLat;
    @Column
    private Double endAnchorLng;

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
