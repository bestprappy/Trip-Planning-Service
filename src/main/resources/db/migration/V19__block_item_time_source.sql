-- Whether a stop's time was set by the traveller ('fixed') or by Calculate times ('calculated').
-- NULL means unknown: every existing time was typed by someone, so clients treat it as fixed.
-- Additive and nullable; older releases ignore the column.
ALTER TABLE trip.block_item ADD COLUMN time_source varchar(16) NULL;
ALTER TABLE trip.block_item ADD CONSTRAINT block_item_time_source_value
    CHECK (time_source IS NULL OR time_source IN ('fixed', 'calculated'));
