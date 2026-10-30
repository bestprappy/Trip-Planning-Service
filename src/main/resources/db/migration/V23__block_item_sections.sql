-- A section divider inside a day: an optional colour for the stops after it and an
-- optional city for that part of the day. Additive and nullable; older releases ignore
-- the columns, and every existing row keeps its meaning.
ALTER TABLE trip.block_item ADD COLUMN section_color VARCHAR(32) NULL;
ALTER TABLE trip.block_item ADD COLUMN section_destination_id VARCHAR(512) NULL;
ALTER TABLE trip.block_item ADD COLUMN section_destination_name VARCHAR(255) NULL;
ALTER TABLE trip.block_item ADD COLUMN section_destination_lat DOUBLE PRECISION NULL;
ALTER TABLE trip.block_item ADD COLUMN section_destination_lng DOUBLE PRECISION NULL;
ALTER TABLE trip.block_item ADD COLUMN section_destination_country VARCHAR(255) NULL;
ALTER TABLE trip.block_item ADD CONSTRAINT ck_item_section_destination CHECK (
    (section_destination_id IS NULL AND section_destination_name IS NULL
        AND section_destination_lat IS NULL AND section_destination_lng IS NULL)
    OR (section_destination_id IS NOT NULL AND section_destination_name IS NOT NULL
        AND section_destination_lat IS NOT NULL AND section_destination_lng IS NOT NULL
        AND section_destination_lat BETWEEN -90 AND 90
        AND section_destination_lng BETWEEN -180 AND 180)
);
