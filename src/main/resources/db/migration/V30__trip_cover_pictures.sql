-- Owner-uploaded trip banner. Pixels are re-encoded by the service before they
-- are stored; no client filename or external URL is kept. Additive: the trip row
-- is untouched, so the planner version and existing readers are unaffected.
CREATE TABLE trip.trip_cover_picture (
    trip_id UUID PRIMARY KEY REFERENCES trip.trip(id) ON DELETE CASCADE,
    id UUID NOT NULL UNIQUE,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg')),
    image_bytes BYTEA NOT NULL CHECK (octet_length(image_bytes) BETWEEN 1 AND 5242880),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
