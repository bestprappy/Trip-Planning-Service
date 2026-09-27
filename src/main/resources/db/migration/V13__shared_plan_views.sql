ALTER TABLE trip.trip_publication
    ADD COLUMN view_count bigint NOT NULL DEFAULT 0;

CREATE INDEX trip_publication_trending_idx
    ON trip.trip_publication (view_count DESC, listed_at DESC)
    WHERE listed_in_explore = true AND status = 'ACTIVE' AND token IS NOT NULL;
