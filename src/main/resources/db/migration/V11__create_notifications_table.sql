CREATE TABLE notifications (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type           VARCHAR(50)  NOT NULL,   -- e.g. RESERVATION_CONFIRMED, RESERVATION_REJECTED, MAINTENANCE_CONFLICT
    title          VARCHAR(150) NOT NULL,
    message        VARCHAR(500) NOT NULL,
    reservation_id BIGINT       REFERENCES reservations (id) ON DELETE SET NULL,
    is_read        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications (user_id, is_read, created_at DESC);
