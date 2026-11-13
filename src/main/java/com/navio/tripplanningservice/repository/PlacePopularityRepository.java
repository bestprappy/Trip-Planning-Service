package com.navio.tripplanningservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlacePopularityRepository {
    private final JdbcClient jdbc;

    public record Count(String placeId, long travelers) {}
    public record PublicSource(String placeId, String json) {}

    public boolean contributes(UUID user) {
        return jdbc.sql("SELECT contributes FROM trip.place_popularity_preference WHERE user_id = :user")
                .param("user", user).query(Boolean.class).optional().orElse(true);
    }

    public void setContributes(UUID user, boolean contributes) {
        jdbc.sql("""
                INSERT INTO trip.place_popularity_preference(user_id, contributes)
                VALUES (:user, :contributes)
                ON CONFLICT (user_id) DO UPDATE
                SET contributes = EXCLUDED.contributes, updated_at = CURRENT_TIMESTAMP
                """).param("user", user).param("contributes", contributes).update();
    }

    /** Counts current saved itinerary membership, never clicks, My List or publication copies. */
    public List<Count> nearby(double lat, double lng, int minimum, List<UUID> excludedAccounts) {
        return jdbc.sql("""
                WITH eligible AS (
                    SELECT t.user_id,
                        CASE
                            WHEN i.place_id ~ '^(GOOGLE|MAPBOX):[^[:space:]]+$' THEN i.place_id
                            WHEN i.place_id ~ '^ChI[A-Za-z0-9_-]+$' THEN 'GOOGLE:' || i.place_id
                        END AS canonical_id
                    FROM trip.block_item i
                    JOIN trip.list_block b ON b.id = i.block_id AND b.type = 'ITINERARY'
                    JOIN trip.trip t ON t.id = b.trip_id
                    LEFT JOIN trip.place_popularity_preference pref ON pref.user_id = t.user_id
                    WHERE i.type = 'PLACE' AND COALESCE(pref.contributes, TRUE)
                      AND t.user_id NOT IN (:excluded)
                      AND i.client_id NOT LIKE 'copied-item-%'
                      AND COALESCE(i.place_type, '') NOT IN ('street_address', 'premise', 'subpremise', 'route')
                      AND i.place_lat BETWEEN :lat - 0.45 AND :lat + 0.45
                      AND i.place_lat BETWEEN -90 AND 90
                      AND i.place_lng BETWEEN -180 AND 180
                      AND 6371.0088 * acos(least(1.0, greatest(-1.0,
                          sin(radians(:lat)) * sin(radians(i.place_lat))
                          + cos(radians(:lat)) * cos(radians(i.place_lat))
                          * cos(radians(i.place_lng - :lng))))) <= 50
                )
                SELECT canonical_id, count(DISTINCT user_id) AS travelers
                FROM eligible WHERE canonical_id IS NOT NULL
                GROUP BY canonical_id HAVING count(DISTINCT user_id) >= :minimum
                ORDER BY travelers DESC, canonical_id ASC LIMIT 48
                """).param("lat", lat).param("lng", lng).param("minimum", minimum)
                .param("excluded", excludedAccounts)
                .query((row, index) -> new Count(row.getString("canonical_id"), row.getLong("travelers"))).list();
    }

    /** Never project names, addresses, photos or annotations out of private planner rows. */
    public List<PublicSource> publicSources(List<String> ids, int sanitizerVersion) {
        if (ids.isEmpty()) return List.of();
        return jdbc.sql("""
                WITH public_items AS (
                    SELECT CASE WHEN item->>'placeId' ~ '^ChI[A-Za-z0-9_-]+$'
                                THEN 'GOOGLE:' || (item->>'placeId') ELSE item->>'placeId' END AS place_id,
                           item::text AS json, p.updated_at, p.id
                    FROM trip.trip_publication p
                    CROSS JOIN LATERAL jsonb_array_elements(
                        CASE WHEN jsonb_typeof(p.snapshot->'days') = 'array'
                             THEN p.snapshot->'days' ELSE '[]'::jsonb END) day
                    CROSS JOIN LATERAL jsonb_array_elements(
                        CASE WHEN jsonb_typeof(day->'items') = 'array'
                             THEN day->'items' ELSE '[]'::jsonb END) item
                    WHERE p.listed_in_explore = TRUE AND p.status = 'ACTIVE' AND p.token IS NOT NULL
                      AND p.sanitizer_version >= :version AND item->>'type' = 'place'
                )
                SELECT DISTINCT ON (place_id) place_id, json FROM public_items
                WHERE place_id IN (:ids) ORDER BY place_id, updated_at DESC, id
                """).param("version", sanitizerVersion).param("ids", ids)
                .query((row, index) -> new PublicSource(row.getString("place_id"), row.getString("json"))).list();
    }
}
