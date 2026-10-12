-- Additive compatibility: keep the legacy whole-minute field; no historical backfill.
ALTER TABLE trip.block_item ADD COLUMN charging_duration_minutes double precision NULL
    CHECK (charging_duration_minutes >= 0 AND charging_duration_minutes <= 2147483647);
ALTER TABLE trip.block_item ADD COLUMN planned_departure_soc_pct double precision NULL
    CHECK (planned_departure_soc_pct >= 0 AND planned_departure_soc_pct <= 100);
