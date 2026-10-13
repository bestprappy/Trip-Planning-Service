-- Additive: older services continue to use arrival_mode and ignore this nullable column.
ALTER TABLE trip.block_item ADD COLUMN arrival_route_key text;
ALTER TABLE trip.block_item ADD CONSTRAINT block_item_arrival_route_key_length
    CHECK (arrival_route_key IS NULL OR char_length(arrival_route_key) <= 4096);
