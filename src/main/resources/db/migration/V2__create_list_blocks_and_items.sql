-- Create list_block table
CREATE TABLE trip.list_block (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_list_block_trip FOREIGN KEY (trip_id) REFERENCES trip.trip(id) ON DELETE CASCADE
);

CREATE INDEX idx_list_block_trip_id ON trip.list_block(trip_id);
CREATE INDEX idx_list_block_type ON trip.list_block(type);

-- Create block_item table
CREATE TABLE trip.block_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    block_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    title VARCHAR(255) NOT NULL,
    notes TEXT,

    -- Place fields
    place_id VARCHAR(255),
    place_name VARCHAR(255),
    place_lat DOUBLE PRECISION,
    place_lng DOUBLE PRECISION,
    place_type VARCHAR(100),
    estimated_time TIME,

    -- EV Station fields
    station_id VARCHAR(255),
    station_name VARCHAR(255),
    station_lat DOUBLE PRECISION,
    station_lng DOUBLE PRECISION,
    charger_type VARCHAR(100),
    connector_type VARCHAR(100),
    estimated_charge_minutes INTEGER,
    battery_percentage DOUBLE PRECISION,

    -- Checklist fields
    completed BOOLEAN DEFAULT FALSE,

    -- Reservation fields
    reservation_id VARCHAR(255),
    reservation_type VARCHAR(100),
    reservation_details TEXT,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_block_item_list_block FOREIGN KEY (block_id) REFERENCES trip.list_block(id) ON DELETE CASCADE
);

CREATE INDEX idx_block_item_block_id ON trip.block_item(block_id);
CREATE INDEX idx_block_item_type ON trip.block_item(type);
CREATE INDEX idx_block_item_display_order ON trip.block_item(display_order);
