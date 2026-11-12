-- The approval policy lives on the category:
-- requires_approval = FALSE -> reservations are confirmed automatically
-- requires_approval = TRUE  -> approver_role_id says who must approve
--                              (FACILITY_MANAGER or DEPARTMENT_MANAGER)
CREATE TABLE resource_categories (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             VARCHAR(100) NOT NULL UNIQUE,
    description      VARCHAR(255),
    requires_approval BOOLEAN     NOT NULL DEFAULT FALSE,
    approver_role_id SMALLINT     REFERENCES roles (id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_category_approver
        CHECK (NOT requires_approval OR approver_role_id IS NOT NULL)
);

INSERT INTO resource_categories (name, description, requires_approval, approver_role_id) VALUES
    ('Meeting Room',    'Small and medium meeting rooms',  FALSE, NULL),
    ('Training Room',   'Rooms used for training',         FALSE, NULL),
    ('Equipment',       'Projectors and other equipment',  FALSE, NULL),
    ('Conference Hall', 'Large halls and event spaces',    TRUE,  (SELECT id FROM roles WHERE name = 'FACILITY_MANAGER')),
    ('Vehicle',         'Company vehicles',                TRUE,  (SELECT id FROM roles WHERE name = 'DEPARTMENT_MANAGER'));
