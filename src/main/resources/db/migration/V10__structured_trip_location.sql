ALTER TABLE trip.trip
    ADD COLUMN destination_city varchar(255),
    ADD COLUMN destination_region varchar(255),
    ADD COLUMN destination_country_code char(2),
    ALTER COLUMN display_name DROP NOT NULL;

CREATE INDEX idx_trip_unresolved_location ON trip.trip (id)
    WHERE destination_country_code IS NULL;
