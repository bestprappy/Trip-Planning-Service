package com.navio.tripplanningservice.repository;

import com.navio.tripplanningservice.media.PictureValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Re-encoded trip cover pixels, one per trip. Pixel reads are always scoped to the trip owner. */
@Repository
@RequiredArgsConstructor
public class TripCoverRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public void save(UUID tripId, UUID id, PictureValidator.Picture picture) {
        jdbc.update("""
                INSERT INTO trip.trip_cover_picture(trip_id, id, content_type, image_bytes, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (trip_id) DO UPDATE
                SET id = EXCLUDED.id, content_type = EXCLUDED.content_type,
                    image_bytes = EXCLUDED.image_bytes, updated_at = EXCLUDED.updated_at
                """, tripId, id, picture.contentType(), picture.bytes());
    }

    public Optional<PictureValidator.Picture> findOwned(UUID tripId, UUID userId) {
        return jdbc.query("""
                SELECT c.image_bytes, c.content_type FROM trip.trip_cover_picture c
                JOIN trip.trip t ON t.id = c.trip_id
                WHERE c.trip_id = ? AND t.user_id = ?
                """, (row, index) -> new PictureValidator.Picture(row.getBytes(1), row.getString(2)), tripId, userId)
                .stream().findFirst();
    }

    public Optional<UUID> findCoverId(UUID tripId) {
        return jdbc.query("SELECT id FROM trip.trip_cover_picture WHERE trip_id = ?",
                (row, index) -> row.getObject(1, UUID.class), tripId).stream().findFirst();
    }

    /** Cover ids for a page of trips in one query; trips without a cover are absent. */
    public Map<UUID, UUID> findCoverIds(Collection<UUID> tripIds) {
        Map<UUID, UUID> covers = new HashMap<>();
        if (tripIds.isEmpty()) return covers;
        named.query("SELECT trip_id, id FROM trip.trip_cover_picture WHERE trip_id IN (:ids)",
                new MapSqlParameterSource("ids", tripIds),
                row -> {
                    covers.put(row.getObject(1, UUID.class), row.getObject(2, UUID.class));
                });
        return covers;
    }

    public void delete(UUID tripId) {
        jdbc.update("DELETE FROM trip.trip_cover_picture WHERE trip_id = ?", tripId);
    }
}
