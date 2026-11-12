-- One row per resource per open weekday (1 = Monday ... 7 = Sunday, ISO).
-- No row for a day means the resource is closed that day.
-- Times are local wall-clock times in the application's configured timezone.
CREATE TABLE operating_hours (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource_id BIGINT   NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    open_time   TIME     NOT NULL,
    close_time  TIME     NOT NULL,

    CONSTRAINT chk_oh_day   CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_oh_times CHECK (close_time > open_time),
    CONSTRAINT uq_oh_resource_day UNIQUE (resource_id, day_of_week)
);
