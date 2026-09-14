package com.navio.tripplanningservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trip", schema = "trip")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column
    private String displayName;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    // Destination: cached place information from Mobility Service
    @Column(nullable = false)
    private String destinationId;

    @Column(nullable = false)
    private String destinationName;

    @Column
    private Double destinationLat;

    @Column
    private Double destinationLng;

    @Column
    private String destinationCountry;

    @Column
    private String destinationCity;

    @Column
    private String destinationRegion;

    // Matches the char(2) column from V10; production validates the schema.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2, columnDefinition = "char(2)")
    private String destinationCountryCode;

    // Visibility: PRIVATE, UNLISTED, PUBLIC
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TripVisibility visibility;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode budgetCurrency = CurrencyCode.THB;

    @Builder.Default
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal budgetAmount = BigDecimal.ZERO;

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
        if (version == null) {
            version = 0L;
        }
        if (budgetCurrency == null) {
            budgetCurrency = CurrencyCode.THB;
        }
        if (budgetAmount == null) {
            budgetAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
