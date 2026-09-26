package com.navio.tripplanningservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A trip's single published link and the frozen snapshot it serves.
 *
 * <p>See {@code V11__trip_publication.sql} for why the snapshot is frozen rather
 * than projected on read.
 */
@Entity
@Table(name = "trip_publication", schema = "trip")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripPublication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tripId;

    /**
     * The bearer secret in the shared URL.
     *
     * <p>Excluded from {@code toString()} on purpose: this entity reaching a log
     * line, an error body or an APM trace must not carry the token with it.
     * Anyone who reads the token has the plan.
     */
    @ToString.Exclude
    @Column(length = 64)
    private String token;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(nullable = false)
    private Integer sanitizerVersion;

    @Column(nullable = false)
    private Long sourceTripVersion;

    @Builder.Default
    @Column(nullable = false)
    private Boolean includeDates = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean includeNotes = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean includeBudget = false;

    /** Also shown on the public Explore page. A separate opt-in from the link itself. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean listedInExplore = false;

    @Column
    private Instant listedAt;

    /** Byline frozen at publish/list time; see V11. */
    @Column(length = 120)
    private String authorDisplayName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicationStatus status = PublicationStatus.ACTIVE;

    @Builder.Default
    @Column(nullable = false)
    private Integer revision = 1;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private Instant publishedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private Instant revokedAt;

    public boolean isActive() {
        return status == PublicationStatus.ACTIVE && token != null;
    }

    public boolean isListed() {
        return isActive() && Boolean.TRUE.equals(listedInExplore);
    }

    /**
     * Lists or unlists the plan. Re-listing an already listed plan keeps its
     * original {@code listedAt}, so pressing Update does not bump it to the top
     * of Explore every time.
     */
    public void applyListing(boolean listed, Instant now) {
        if (listed && !Boolean.TRUE.equals(listedInExplore)) {
            listedAt = now;
        }
        if (!listed) {
            listedAt = null;
        }
        listedInExplore = listed;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (publishedAt == null) {
            publishedAt = now;
        }
        updatedAt = now;
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum PublicationStatus {
        ACTIVE,
        REVOKED
    }
}
