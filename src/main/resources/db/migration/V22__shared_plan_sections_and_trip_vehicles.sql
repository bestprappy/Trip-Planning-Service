-- Additive: older releases ignore these nullable fields; omitted saves preserve them.
ALTER TABLE trip.trip ADD COLUMN trip_vehicles jsonb;
ALTER TABLE trip.trip ADD CONSTRAINT trip_vehicles_array CHECK (trip_vehicles IS NULL OR (jsonb_typeof(trip_vehicles) = 'array' AND jsonb_array_length(trip_vehicles) <= 25));
ALTER TABLE trip.trip_publication ADD COLUMN selection_options jsonb;
