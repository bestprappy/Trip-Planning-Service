-- Per-day start and end anchors.
--
-- Only explicit choices are stored. A day's start is normally *derived* from the
-- previous day's end ("you wake up where you went to sleep"), so start_anchor_*
-- is written only when the traveller overrides that — most importantly on day
-- one, where it is the trip's origin, typically the user's home.
--
-- anchor kind drives what happens when a trip is shared or published:
--   SAVED_PLACE  a row from iam.user_saved_places — personal, and possibly a
--                home address. MUST be stripped and replaced with a placeholder
--                by any snapshot serialiser. Never publish one.
--   PLACE        a provider place (a hotel, a landmark). Real itinerary content;
--                it is what makes a shared template useful, so it travels.
--   MANUAL       an ad-hoc pin the user typed. Travels.
--
-- Nullable throughout so existing itineraries keep working untouched.
ALTER TABLE trip.list_block
    ADD COLUMN start_anchor_id      VARCHAR(512),
    ADD COLUMN start_anchor_kind    VARCHAR(20),
    ADD COLUMN start_anchor_name    VARCHAR(255),
    ADD COLUMN start_anchor_address VARCHAR(512),
    ADD COLUMN start_anchor_lat     DOUBLE PRECISION,
    ADD COLUMN start_anchor_lng     DOUBLE PRECISION,
    ADD COLUMN end_anchor_id        VARCHAR(512),
    ADD COLUMN end_anchor_kind      VARCHAR(20),
    ADD COLUMN end_anchor_name      VARCHAR(255),
    ADD COLUMN end_anchor_address   VARCHAR(512),
    ADD COLUMN end_anchor_lat       DOUBLE PRECISION,
    ADD COLUMN end_anchor_lng       DOUBLE PRECISION;

-- An anchor is present in full or not at all. A half-written anchor would put a
-- named place at null coordinates, which silently breaks route and EV maths
-- rather than failing loudly.
ALTER TABLE trip.list_block
    ADD CONSTRAINT ck_block_start_anchor CHECK (
        (start_anchor_id IS NULL AND start_anchor_kind IS NULL AND start_anchor_name IS NULL
            AND start_anchor_lat IS NULL AND start_anchor_lng IS NULL)
        OR (start_anchor_id IS NOT NULL AND start_anchor_name IS NOT NULL
            AND start_anchor_kind IN ('SAVED_PLACE', 'PLACE', 'MANUAL')
            AND start_anchor_lat BETWEEN -90 AND 90
            AND start_anchor_lng BETWEEN -180 AND 180)
    ),
    ADD CONSTRAINT ck_block_end_anchor CHECK (
        (end_anchor_id IS NULL AND end_anchor_kind IS NULL AND end_anchor_name IS NULL
            AND end_anchor_lat IS NULL AND end_anchor_lng IS NULL)
        OR (end_anchor_id IS NOT NULL AND end_anchor_name IS NOT NULL
            AND end_anchor_kind IN ('SAVED_PLACE', 'PLACE', 'MANUAL')
            AND end_anchor_lat BETWEEN -90 AND 90
            AND end_anchor_lng BETWEEN -180 AND 180)
    );
