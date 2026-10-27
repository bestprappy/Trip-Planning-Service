-- Historical successful imports. No FK to copied trips: deleting a private copy must
-- not erase the fact that a plan was used. No cross-service account FK or public identities.
CREATE TABLE trip.shared_plan_copy (
    copied_trip_id UUID PRIMARY KEY,
    publication_id UUID NOT NULL REFERENCES trip.trip_publication(id) ON DELETE CASCADE,
    copied_by UUID NOT NULL,
    copied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX shared_plan_copy_publication_user_idx ON trip.shared_plan_copy(publication_id, copied_by);

-- Recover only existing copies that still carry the original import reference. Deleted
-- historical copies cannot be reconstructed and must not be invented.
INSERT INTO trip.shared_plan_copy (copied_trip_id, publication_id, copied_by, copied_at)
SELECT t.id, p.id, t.user_id, t.created_at FROM trip.trip t
JOIN trip.trip_publication p ON t.destination_id = 'shared-plan:' || p.id::text
ON CONFLICT (copied_trip_id) DO NOTHING;
