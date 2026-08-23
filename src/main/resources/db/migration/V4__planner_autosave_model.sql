ALTER TABLE trip.trip
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE trip.list_block
    ADD COLUMN IF NOT EXISTS client_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS block_date DATE,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

UPDATE trip.list_block
SET client_id = id::text
WHERE client_id IS NULL;

UPDATE trip.list_block
SET block_date = CURRENT_DATE
WHERE block_date IS NULL;

ALTER TABLE trip.list_block
    ALTER COLUMN client_id SET NOT NULL,
    ALTER COLUMN block_date SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_list_block_trip_client
    ON trip.list_block(trip_id, client_id);

ALTER TABLE trip.block_item
    ADD COLUMN IF NOT EXISTS client_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS place_review_count INTEGER,
    ADD COLUMN IF NOT EXISTS place_description TEXT,
    ADD COLUMN IF NOT EXISTS place_image_url TEXT,
    ADD COLUMN IF NOT EXISTS visited BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS start_time TIME,
    ADD COLUMN IF NOT EXISTS end_time TIME,
    ADD COLUMN IF NOT EXISTS estimated_cost NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS ev_connector_types TEXT,
    ADD COLUMN IF NOT EXISTS ev_total_connectors INTEGER,
    ADD COLUMN IF NOT EXISTS ev_available_connectors INTEGER,
    ADD COLUMN IF NOT EXISTS ev_operator_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

UPDATE trip.block_item
SET client_id = id::text
WHERE client_id IS NULL;

ALTER TABLE trip.block_item
    ALTER COLUMN client_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_block_item_block_client
    ON trip.block_item(block_id, client_id);

CREATE TABLE IF NOT EXISTS trip.checklist_sub_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES trip.block_item(id) ON DELETE CASCADE,
    client_id VARCHAR(160) NOT NULL,
    label VARCHAR(500) NOT NULL DEFAULT '',
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_checklist_sub_item_order
    ON trip.checklist_sub_item(item_id, display_order);

CREATE UNIQUE INDEX IF NOT EXISTS uq_checklist_sub_item_client
    ON trip.checklist_sub_item(item_id, client_id);
