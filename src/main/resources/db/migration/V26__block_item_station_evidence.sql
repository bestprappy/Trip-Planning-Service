-- What the provider last said about a saved charging stop: whether it was open, when that was
-- checked and by whom, and each plug's own top speed. Additive and nullable; older releases ignore
-- the columns, and a NULL means the stop was saved before this evidence was kept.
ALTER TABLE trip.block_item ADD COLUMN ev_station_status VARCHAR(32) NULL;
ALTER TABLE trip.block_item ADD COLUMN ev_status_source VARCHAR(32) NULL;
ALTER TABLE trip.block_item ADD COLUMN ev_status_checked_at TIMESTAMPTZ NULL;
ALTER TABLE trip.block_item ADD COLUMN ev_connector_power_kw JSONB NULL;
