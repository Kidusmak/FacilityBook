-- Needed so a GiST index can combine "=" on resource_id with "&&" on a time range.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservations (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource_id         BIGINT       NOT NULL REFERENCES resources (id),
    requester_id        BIGINT       NOT NULL REFERENCES users (id),
    purpose             VARCHAR(300) NOT NULL,
    notes               TEXT,
    start_time          TIMESTAMPTZ  NOT NULL,
    end_time            TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    needs_resolution    BOOLEAN      NOT NULL DEFAULT FALSE,  -- flagged by a conflicting maintenance window
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(300),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_reservation_times  CHECK (end_time > start_time),
    CONSTRAINT chk_reservation_status CHECK (status IN
        ('PENDING_APPROVAL', 'CONFIRMED', 'REJECTED', 'CANCELLED')),

    -- Database-level double-booking protection.
    -- Two active reservations for the same resource cannot have overlapping time ranges.
    -- tstzrange defaults to [start, end), so back-to-back bookings (10-11, 11-12) are allowed.
    CONSTRAINT no_overlapping_reservations
        EXCLUDE USING gist (
            resource_id WITH =,
            tstzrange(start_time, end_time) WITH &&
        )
        WHERE (status IN ('PENDING_APPROVAL', 'CONFIRMED'))
);

CREATE INDEX idx_reservations_requester  ON reservations (requester_id, start_time);
CREATE INDEX idx_reservations_status     ON reservations (status);
CREATE INDEX idx_reservations_start_time ON reservations (start_time);
