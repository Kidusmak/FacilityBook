-- Covers damage reports, scheduled maintenance, and "unavailable for a period" blocks.
-- The service layer rejects reservations that overlap an active maintenance window
-- (SCHEDULED / IN_PROGRESS) and flags existing conflicting reservations.
CREATE TABLE maintenance_records (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource_id  BIGINT       NOT NULL REFERENCES resources (id),
    reported_by  BIGINT       NOT NULL REFERENCES users (id),
    reason       TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'REPORTED',
    start_time   TIMESTAMPTZ  NOT NULL,
    end_time     TIMESTAMPTZ  NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_maintenance_times  CHECK (end_time > start_time),
    CONSTRAINT chk_maintenance_status CHECK (status IN
        ('REPORTED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_maintenance_resource_period ON maintenance_records (resource_id, start_time, end_time);
CREATE INDEX idx_maintenance_status          ON maintenance_records (status);
