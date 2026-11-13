-- Follows V30's trip-cover pictures. No planner data is copied.
CREATE TABLE trip.place_popularity_preference (
    user_id UUID PRIMARY KEY,
    contributes BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_block_item_popularity_location
    ON trip.block_item (place_lat, place_lng, block_id)
    WHERE type = 'PLACE' AND place_id IS NOT NULL;
