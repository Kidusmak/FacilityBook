CREATE TABLE resources (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    category_id     BIGINT       NOT NULL REFERENCES resource_categories (id),
    building_id     BIGINT       REFERENCES buildings (id),   -- NULL for e.g. vehicles
    floor           VARCHAR(20),
    location_detail VARCHAR(150),
    capacity        INTEGER,                                  -- NULL when not applicable
    features        VARCHAR(500),                             -- e.g. 'Projector, Whiteboard, Video call'
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_resource_capacity CHECK (capacity IS NULL OR capacity > 0),
    CONSTRAINT chk_resource_status   CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_resources_category ON resources (category_id);
CREATE INDEX idx_resources_building ON resources (building_id);
CREATE INDEX idx_resources_status   ON resources (status);
