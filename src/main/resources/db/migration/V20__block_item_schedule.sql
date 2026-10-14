-- A stop's own timetable ("09:00-09:10 Check in", "09:30 Shower") as a JSON array of
-- {id, time, timeEnd, label}. Additive and nullable; older releases ignore the column.
ALTER TABLE trip.block_item ADD COLUMN schedule text NULL;
ALTER TABLE trip.block_item ADD CONSTRAINT block_item_schedule_length
    CHECK (schedule IS NULL OR char_length(schedule) <= 32000);
