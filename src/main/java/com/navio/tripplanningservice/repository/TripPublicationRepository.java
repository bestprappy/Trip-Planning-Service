package com.navio.tripplanningservice.repository;

import com.navio.tripplanningservice.model.TripPublication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripPublicationRepository extends JpaRepository<TripPublication, UUID> {

    Optional<TripPublication> findByTripId(UUID tripId);

    /**
     * The anonymous read path's only query.
     *
     * <p>Deliberately not {@code findByToken}: a revoked row keeps its id and its
     * snapshot but has its token nulled, and the status predicate here means a
     * caller cannot reach one even if a future change reintroduces the column
     * value. Nothing about the owner is queryable from this side.
     */
    Optional<TripPublication> findByTokenAndStatus(String token, TripPublication.PublicationStatus status);

    /**
     * The Explore listing: rows the owner explicitly listed, still active, and
     * frozen by a sanitiser the read path would still serve.
     *
     * <p>Every predicate is in the query rather than filtered afterwards, so a
     * page never comes back short and nothing unlisted is ever loaded.
     */
    @Query("""
            select p from TripPublication p
            where p.listedInExplore = true
              and p.status = com.navio.tripplanningservice.model.TripPublication.PublicationStatus.ACTIVE
              and p.token is not null
              and p.sanitizerVersion >= :minimumSanitizerVersion
            order by p.listedAt desc
            """)
    Page<TripPublication> findListedInExplore(
            @Param("minimumSanitizerVersion") int minimumSanitizerVersion,
            Pageable pageable);

    @Query("""
            select p from TripPublication p
            where p.listedInExplore = true
              and p.status = com.navio.tripplanningservice.model.TripPublication.PublicationStatus.ACTIVE
              and p.token is not null
              and p.sanitizerVersion >= :minimumSanitizerVersion
            order by p.viewCount desc, p.listedAt desc
            """)
    Page<TripPublication> findTrendingInExplore(
            @Param("minimumSanitizerVersion") int minimumSanitizerVersion,
            Pageable pageable);

    /**
     * One owner's plans for their public profile: listed on Explore, active, and
     * published under their name. A plan listed anonymously is never attributed.
     * Pinned plans come first.
     */
    @Query("""
            select p from TripPublication p, Trip t
            where t.id = p.tripId and t.userId = :owner
              and p.listedInExplore = true
              and p.status = com.navio.tripplanningservice.model.TripPublication.PublicationStatus.ACTIVE
              and p.token is not null
              and p.authorDisplayName is not null
              and p.sanitizerVersion >= :minimumSanitizerVersion
            order by p.profilePinnedAt desc nulls last, p.listedAt desc
            """)
    Page<TripPublication> findListedByOwner(
            @Param("owner") UUID owner,
            @Param("minimumSanitizerVersion") int minimumSanitizerVersion,
            Pageable pageable);

    @Query("""
            select count(p) from TripPublication p, Trip t
            where t.id = p.tripId and t.userId = :owner and p.profilePinnedAt is not null
              and p.listedInExplore = true
              and p.status = com.navio.tripplanningservice.model.TripPublication.PublicationStatus.ACTIVE
            """)
    long countPinnedByOwner(@Param("owner") UUID owner);

    @Modifying
    @Query("update TripPublication p set p.viewCount = p.viewCount + 1 where p.id = :id")
    void incrementViewCount(@Param("id") UUID id);

    void deleteByTripId(UUID tripId);
}
