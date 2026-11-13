-- One row per browser that has opened a listed plan, so a reload is not a new view.
-- The visitor id is a random per-browser cookie value, not an account, so signed-in
-- and signed-out readers count the same way. Existing view_count totals are kept.
CREATE TABLE trip.publication_viewer (
    publication_id uuid NOT NULL REFERENCES trip.trip_publication(id) ON DELETE CASCADE,
    visitor_id uuid NOT NULL,
    first_viewed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (publication_id, visitor_id)
);
