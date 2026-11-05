-- Counts only events recorded after this migration; never infer historical activity.
CREATE TABLE trip.publication_daily_activity (
 publication_id uuid NOT NULL REFERENCES trip.trip_publication(id) ON DELETE CASCADE,
 activity_day date NOT NULL,
 views bigint NOT NULL DEFAULT 0 CHECK (views >= 0),
 copies bigint NOT NULL DEFAULT 0 CHECK (copies >= 0),
 PRIMARY KEY(publication_id,activity_day)
);
CREATE INDEX publication_activity_window ON trip.publication_daily_activity(activity_day);
