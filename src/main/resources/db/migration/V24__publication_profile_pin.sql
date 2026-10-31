-- The owner's choice to feature a listed plan on their public profile.
--
-- Additive and nullable, so the previous image keeps reading and writing
-- publications after a rollback. Unlisting or stopping sharing clears it in the
-- application; the profile query also requires the plan to be listed, active and
-- published under the owner's name, so a stale value can never expose a plan.
ALTER TABLE trip.trip_publication ADD COLUMN IF NOT EXISTS profile_pinned_at TIMESTAMP WITH TIME ZONE;

-- The profile query: one owner's listed plans. trip(user_id) is already indexed.
CREATE INDEX IF NOT EXISTS idx_trip_publication_trip_listed
    ON trip.trip_publication (trip_id)
    WHERE listed_in_explore = TRUE AND status = 'ACTIVE';
