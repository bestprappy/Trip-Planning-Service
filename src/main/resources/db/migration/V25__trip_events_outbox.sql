-- Transactional outbox for the trip.events.v1 topic.
--
-- A row is written in the same transaction as the publication change it
-- describes, then a relay publishes it to Kafka and stamps published_at after the
-- broker acknowledges. A rolled-back publish therefore produces no event, and a
-- Kafka outage leaves a backlog instead of failing the owner's publish.
--
-- Additive only: the previous image never reads this table, so a rollback keeps
-- working with it in place. Published rows are kept for a retention window so an
-- operator can replay an event with its original id (set published_at to NULL).
CREATE TABLE IF NOT EXISTS trip.outbox
(
    id              UUID         PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    partition_key   VARCHAR(100) NOT NULL,
    payload_jsonb   JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      TEXT
);

-- The relay's scan: due, unpublished rows in creation order.
CREATE INDEX IF NOT EXISTS idx_trip_outbox_pending
    ON trip.outbox (next_attempt_at, created_at)
    WHERE published_at IS NULL;

-- The retention sweep over acknowledged rows.
CREATE INDEX IF NOT EXISTS idx_trip_outbox_published
    ON trip.outbox (published_at)
    WHERE published_at IS NOT NULL;
