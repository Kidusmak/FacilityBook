-- One row per approval decision. A reservation waiting for approval simply has no row yet.
CREATE TABLE reservation_approvals (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT       NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    approver_id    BIGINT       NOT NULL REFERENCES users (id),
    decision       VARCHAR(20)  NOT NULL,
    reason         VARCHAR(500),
    decided_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_approval_decision CHECK (decision IN ('APPROVED', 'REJECTED'))
);

CREATE INDEX idx_approvals_reservation ON reservation_approvals (reservation_id);
CREATE INDEX idx_approvals_approver    ON reservation_approvals (approver_id);
