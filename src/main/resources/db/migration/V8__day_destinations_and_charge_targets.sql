-- Nullable additions preserve existing itineraries and duration-based charging stops.
ALTER TABLE trip.list_block ADD COLUMN destination_id VARCHAR(512);
ALTER TABLE trip.list_block ADD COLUMN destination_name VARCHAR(255);
ALTER TABLE trip.list_block ADD COLUMN destination_lat DOUBLE PRECISION;
ALTER TABLE trip.list_block ADD COLUMN destination_lng DOUBLE PRECISION;
ALTER TABLE trip.list_block ADD COLUMN destination_country VARCHAR(255);
ALTER TABLE trip.list_block ADD CONSTRAINT ck_block_destination_coordinates CHECK (
    (destination_id IS NULL AND destination_name IS NULL AND destination_lat IS NULL AND destination_lng IS NULL)
    OR (destination_id IS NOT NULL AND destination_name IS NOT NULL AND destination_lat IS NOT NULL AND destination_lng IS NOT NULL AND destination_lat BETWEEN -90 AND 90 AND destination_lng BETWEEN -180 AND 180)
);
ALTER TABLE trip.block_item ADD COLUMN target_battery_pct INTEGER;
ALTER TABLE trip.block_item ADD CONSTRAINT ck_item_charge_target CHECK (target_battery_pct BETWEEN 0 AND 100);
