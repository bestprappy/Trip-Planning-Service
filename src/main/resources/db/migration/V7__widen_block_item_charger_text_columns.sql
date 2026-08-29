-- Provider-supplied charger metadata overflowed the original VARCHAR(100) columns.
-- Google Places returns opening hours as seven weekday descriptions joined together,
-- which is routinely 150-260 characters and failed the planner autosave insert.
ALTER TABLE trip.block_item
    ALTER COLUMN opening_hours TYPE VARCHAR(255),
    ALTER COLUMN price TYPE VARCHAR(255);
