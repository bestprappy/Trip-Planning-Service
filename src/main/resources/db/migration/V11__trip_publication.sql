-- Published plan links.
--
-- One row per trip: a trip is either unpublished (no row) or has exactly one
-- link. Replacing a link rotates the token in place; it never adds a second row,
-- so a trip can never be reachable through two URLs the owner has to reason
-- about separately.
--
-- The snapshot is FROZEN at publish time, not projected on read. Two reasons:
--   1. "Publish" has to mean something. The owner's later edits — a new personal
--      note, a changed hotel — stay private until they press Update. A read-time
--      projection would leak every edit the moment it was saved.
--   2. Revocation is then a single-row change with nothing cached downstream.
--
-- The cost of freezing is that a later fix to the sanitiser does not reach rows
-- written before it, so sanitizer_version records which ruleset produced this
-- snapshot and the read path refuses anything below its current version. A
-- redaction fix therefore dead-links stale snapshots rather than silently
-- serving them under the old rules; the owner re-publishes to get the link back.
CREATE TABLE IF NOT EXISTS trip.trip_publication (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ON DELETE CASCADE: deleting a trip must take its public link with it.
    -- A surviving publication row would be an orphaned readable snapshot of a
    -- trip the owner believes they deleted.
    trip_id             UUID NOT NULL REFERENCES trip.trip(id) ON DELETE CASCADE,

    -- The bearer secret. NULL once sharing is stopped, which frees the unique
    -- index so a later re-publish can mint a fresh token; the old value is gone
    -- rather than disabled, so a stopped link can never be reactivated.
    -- Never log this column and never send it to analytics.
    token               VARCHAR(64),

    -- The sanitised public snapshot. Contains no owner id, no coordinates and no
    -- personal anchors; see PlanPublicationSanitizer, which is the only writer.
    snapshot            JSONB NOT NULL,
    sanitizer_version   INTEGER NOT NULL,

    -- trip.version the snapshot was taken from. Drives the "Unpublished changes"
    -- indicator and lets a publish reject a source that moved under it.
    source_trip_version BIGINT NOT NULL,

    -- Owner opt-ins. Default FALSE everywhere: a column added later without a
    -- default, or a settings row that fails to load, must publish less, not more.
    include_dates       BOOLEAN NOT NULL DEFAULT FALSE,
    include_notes       BOOLEAN NOT NULL DEFAULT FALSE,
    include_budget      BOOLEAN NOT NULL DEFAULT FALSE,

    -- A second, separate opt-in: also list this plan on the public Explore page.
    -- Off by default because Explore turns "people I gave the link to" into
    -- "anyone browsing Navio". Listing serves the same frozen snapshot as the
    -- link; it adds discovery, not content.
    listed_in_explore   BOOLEAN NOT NULL DEFAULT FALSE,
    -- When it was (last) listed. Orders Explore newest-first; NULL when unlisted.
    listed_at           TIMESTAMP WITH TIME ZONE,

    -- The owner's display name as shown on the published plan, frozen when they
    -- publish or list, like a byline. Stored here rather than looked up by owner
    -- id on read, so the anonymous routes never carry an account identifier.
    -- NULL renders as "a Navio traveler".
    author_display_name VARCHAR(120),

    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- Counts successful publishes; the client sends it back so a duplicated
    -- click cannot overwrite a newer publication.
    revision            INTEGER NOT NULL DEFAULT 1,

    version             BIGINT NOT NULL DEFAULT 0,
    published_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at          TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uq_trip_publication_trip UNIQUE (trip_id),
    CONSTRAINT uq_trip_publication_token UNIQUE (token),
    CONSTRAINT chk_trip_publication_status CHECK (status IN ('ACTIVE', 'REVOKED')),

    -- An ACTIVE row without a token would be unreachable; a REVOKED row holding
    -- one would still be readable. Both are the bug that matters here, so the
    -- database refuses them rather than trusting the service to be consistent.
    CONSTRAINT chk_trip_publication_token_matches_status CHECK (
        (status = 'ACTIVE' AND token IS NOT NULL)
        OR (status = 'REVOKED' AND token IS NULL)
    ),

    -- Stopping sharing must also take the plan off Explore. A REVOKED row that
    -- stayed listed would be a listing with nothing behind it at best, and a
    -- listing that outlived the owner's decision at worst.
    CONSTRAINT chk_trip_publication_listed_only_when_active CHECK (
        listed_in_explore = FALSE OR (status = 'ACTIVE' AND listed_at IS NOT NULL)
    )
);

-- The anonymous read path's only query. Partial: revoked rows have a NULL token
-- and are never looked up.
CREATE UNIQUE INDEX IF NOT EXISTS idx_trip_publication_active_token
    ON trip.trip_publication (token)
    WHERE token IS NOT NULL;

-- The Explore listing query: listed, active rows, newest first.
CREATE INDEX IF NOT EXISTS idx_trip_publication_explore
    ON trip.trip_publication (listed_at DESC)
    WHERE listed_in_explore = TRUE AND status = 'ACTIVE';
