-- Additive: historical trips/stops retain absent state; no data backfill.
ALTER TABLE trip.trip ADD COLUMN initial_soc_pct numeric(5,2) NULL CHECK (initial_soc_pct BETWEEN 0 AND 100);
ALTER TABLE trip.trip ADD COLUMN energy_vehicle_snapshot jsonb NULL;
ALTER TABLE trip.block_item ADD COLUMN observed_soc_pct numeric(5,2) NULL CHECK (observed_soc_pct BETWEEN 0 AND 100);
