ALTER TABLE trip.block_item
    ADD COLUMN ev_selection_source VARCHAR(16),
    ADD COLUMN ev_locked BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE trip.block_item
SET ev_selection_source = 'MANUAL'
WHERE place_id LIKE 'ev-charger:%'
  AND ev_selection_source IS NULL;
