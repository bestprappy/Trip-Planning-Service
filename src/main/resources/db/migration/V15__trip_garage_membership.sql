-- Account vehicles remain reusable; membership belongs to each trip.
-- NULL means historical/unspecified, [] is an explicitly empty trip garage.
ALTER TABLE trip.trip ADD COLUMN garage_vehicle_ids jsonb NULL;
