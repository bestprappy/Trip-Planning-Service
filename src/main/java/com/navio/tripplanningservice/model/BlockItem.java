package com.navio.tripplanningservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "block_item", schema = "trip")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID blockId;

    @Column(nullable = false, length = 160)
    private String clientId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BlockItemType type;

    @Column(nullable = false)
    private Integer displayOrder;

    // ============ COMMON FIELDS ============
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ============ PLACE ITEM FIELDS ============
    private String placeId;           // Reference to Mobility Service place
    private String placeName;         // Cached place name
    private Double placeLat;          // Cached latitude
    private Double placeLng;          // Cached longitude
    private String placeType;         // Category: restaurant, hotel, landmark, etc.
    private String placeAddress;      // Full formatted address for display
    private String placePhone;        // Contact phone number
    private String placeWebsite;      // Website or booking link
    private Double placeRating;       // Star rating (e.g., 4.5)
    private Integer placeReviewCount;

    @Column(columnDefinition = "TEXT")
    private String placeDescription;

    @Column(columnDefinition = "TEXT")
    private String placeImageUrl;

    private Boolean visited;
    private LocalTime startTime;
    private LocalTime endTime;

    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    private LocalTime estimatedTime;  // Legacy estimated visit time

    // ============ EV STATION FIELDS ============
    private String stationId;         // Reference to Mobility Service charger
    private String stationName;       // Cached station name
    private String stationAddress;    // Full formatted address
    private Double stationLat;        // Cached latitude
    private Double stationLng;        // Cached longitude
    private String chargerType;       // DC Fast, Level 2, etc.
    private String connectorType;     // Tesla, CCS, CHAdeMO, etc.
    private Integer availablePlugs;   // Number of available connectors
    private Double powerKw;           // Power rating in kW (e.g., 22.0)
    private Integer estimatedChargeMinutes;  // How long to charge
    private Double batteryPercentage; // Why we're stopping (low battery %)
    private String price;             // Price info (e.g., "$3.50/kWh" or "Price not listed")
    private String openingHours;      // Operating hours (e.g., "24/7" or "Hours not listed")
    private Boolean isCharged;        // User marked as charged/completed

    @Column(columnDefinition = "TEXT")
    private String evConnectorTypes;
    private Integer evTotalConnectors;
    private Integer evAvailableConnectors;
    private String evOperatorName;

    // ============ CHECKLIST ITEM FIELDS ============
    private Boolean completed;        // Checklist completion status

    // ============ RESERVATION FIELDS ============
    private String reservationId;     // External booking ID
    private String reservationType;   // Hotel, Restaurant, Activity, etc.
    private String reservationDetails; // Confirmation details

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
        if (completed == null) {
            completed = false;
        }
        if (isCharged == null) {
            isCharged = false;
        }
        if (visited == null) {
            visited = false;
        }
        if (clientId == null) {
            clientId = UUID.randomUUID().toString();
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum BlockItemType {
        PLACE,          // Sightseeing, restaurant, activity, etc.
        EV_STATION,     // Charging stop
        CHECKLIST,      // Checklist container with ordered sub-items
        CHECKLIST_ITEM, // Task to complete
        NOTE,           // Free-form note
        RESERVATION     // Hotel booking, restaurant reservation, etc.
    }
}
