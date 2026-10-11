-- How the traveller reaches a stop from the previous one: 'drive', 'transit' or 'walk'.
-- NULL means automatic (transit between two stations, otherwise drive), which is also how
-- every existing stop behaves, so no backfill is needed and older releases ignore the column.
ALTER TABLE trip.block_item ADD COLUMN arrival_mode varchar(16) NULL;
