ALTER TABLE trip.trip
    ADD COLUMN IF NOT EXISTS budget_currency VARCHAR(3) NOT NULL DEFAULT 'THB',
    ADD COLUMN IF NOT EXISTS budget_amount NUMERIC(14, 2) NOT NULL DEFAULT 0;

ALTER TABLE trip.trip
    ADD CONSTRAINT chk_trip_budget_amount_non_negative
        CHECK (budget_amount >= 0);

CREATE TABLE IF NOT EXISTS trip.expense (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trip.trip(id) ON DELETE CASCADE,
    client_id VARCHAR(160) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    label VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    expense_date DATE,
    display_order INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_expense_amount_positive CHECK (amount > 0),
    CONSTRAINT uq_expense_trip_client UNIQUE (trip_id, client_id)
);

CREATE INDEX IF NOT EXISTS idx_expense_trip_order
    ON trip.expense(trip_id, display_order);

CREATE INDEX IF NOT EXISTS idx_expense_trip_category
    ON trip.expense(trip_id, category);
