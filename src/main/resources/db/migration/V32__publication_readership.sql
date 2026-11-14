-- Preserve existing total counts; historical signed-in identities cannot be reconstructed.
CREATE TABLE trip.publication_read_receipt (
 publication_id uuid NOT NULL REFERENCES trip.trip_publication(id) ON DELETE CASCADE,
 read_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(publication_id,read_id)
);
CREATE TABLE trip.publication_account_viewer (
 publication_id uuid NOT NULL REFERENCES trip.trip_publication(id) ON DELETE CASCADE,
 user_id uuid NOT NULL,
 first_viewed_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(publication_id,user_id)
);
