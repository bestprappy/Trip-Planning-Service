-- Add color field to list_block
ALTER TABLE trip.list_block ADD COLUMN block_color VARCHAR(50);

-- Add missing fields to block_item for PLACE items
ALTER TABLE trip.block_item
    ADD COLUMN place_address VARCHAR(500),
    ADD COLUMN place_phone VARCHAR(20),
    ADD COLUMN place_website VARCHAR(255),
    ADD COLUMN place_rating DOUBLE PRECISION;

-- Add missing fields to block_item for EV STATION items
ALTER TABLE trip.block_item
    ADD COLUMN station_address VARCHAR(500),
    ADD COLUMN available_plugs INTEGER,
    ADD COLUMN power_kw DOUBLE PRECISION,
    ADD COLUMN price VARCHAR(100),
    ADD COLUMN opening_hours VARCHAR(100),
    ADD COLUMN is_charged BOOLEAN DEFAULT FALSE;

-- Create index for charging status query
CREATE INDEX idx_block_item_is_charged ON trip.block_item(is_charged);
