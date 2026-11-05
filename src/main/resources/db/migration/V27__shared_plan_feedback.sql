-- Additive: old images ignore these tables and read-only aggregate columns.
ALTER TABLE trip.trip_publication
    ADD COLUMN rating_count BIGINT NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
    ADD COLUMN rating_sum BIGINT NOT NULL DEFAULT 0 CHECK (rating_sum >= 0),
    ADD COLUMN comment_count BIGINT NOT NULL DEFAULT 0 CHECK (comment_count >= 0);

CREATE TABLE trip.plan_rating (
    publication_id UUID NOT NULL REFERENCES trip.trip_publication(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    score INTEGER NOT NULL CHECK (score BETWEEN 1 AND 5),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (publication_id, user_id)
);
CREATE TABLE trip.plan_comment (
    id UUID PRIMARY KEY,
    publication_id UUID NOT NULL REFERENCES trip.trip_publication(id) ON DELETE CASCADE,
    parent_id UUID,
    user_id UUID NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    content VARCHAR(1500) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id, publication_id),
    FOREIGN KEY (parent_id, publication_id) REFERENCES trip.plan_comment(id, publication_id),
    CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX plan_comment_threads ON trip.plan_comment(publication_id, created_at DESC, id DESC) WHERE parent_id IS NULL;
CREATE INDEX plan_comment_replies ON trip.plan_comment(publication_id, parent_id, created_at, id) WHERE parent_id IS NOT NULL;
CREATE TABLE trip.plan_comment_report (
    comment_id UUID NOT NULL REFERENCES trip.plan_comment(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL,
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('SPAM','HARASSMENT','INAPPROPRIATE','OTHER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, reporter_id)
);
