-- Create trip schema
CREATE SCHEMA IF NOT EXISTS trip;

-- Create trip table
CREATE TABLE trip.trip (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    destination_id VARCHAR(255) NOT NULL,
    destination_name VARCHAR(255) NOT NULL,
    destination_lat DOUBLE PRECISION,
    destination_lng DOUBLE PRECISION,
    destination_country VARCHAR(100),
    visibility VARCHAR(50) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for common queries
CREATE INDEX idx_trip_user_id ON trip.trip(user_id);
CREATE INDEX idx_trip_visibility ON trip.trip(visibility);
CREATE INDEX idx_trip_created_at ON trip.trip(created_at DESC);
