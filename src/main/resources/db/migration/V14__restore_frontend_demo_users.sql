-- Restore the former frontend demo users as database-backed sample records.
-- These accounts use the same local-demo password as the V12 seed accounts.
INSERT INTO departments (name)
SELECT additions.name
FROM (VALUES
    ('Administration'),
    ('Information Technology'),
    ('Operations')
) AS additions(name)
WHERE NOT EXISTS (
    SELECT 1
    FROM departments existing_department
    WHERE existing_department.name = additions.name
);

INSERT INTO users (email, password_hash, full_name, role_id, department_id, active)
SELECT
    sample.email,
    '$2b$10$wuB4kODw9wVMqdTIbFMkp.iMrS/rkVQU4IadNukr.uJn.QcZdAXMu',
    sample.full_name,
    r.id,
    d.id,
    sample.active
FROM (VALUES
    ('abebe@example.com', 'Abebe Kebede', 'ADMIN', 'Administration', TRUE),
    ('sara@example.com', 'Sara Mohammed', 'EMPLOYEE', 'Information Technology', TRUE),
    ('dawit@example.com', 'Dawit Tesfaye', 'DEPARTMENT_MANAGER', 'Human Resources', TRUE),
    ('martha@example.com', 'Martha Bekele', 'EMPLOYEE', 'Finance', TRUE),
    ('daniel@example.com', 'Daniel Alemu', 'FACILITY_MANAGER', 'Facilities', TRUE),
    ('hana@example.com', 'Hana Girma', 'RESOURCE_OPERATOR', 'Facilities', TRUE),
    ('samuel@example.com', 'Samuel Tadesse', 'EMPLOYEE', 'Engineering', FALSE),
    ('meron@example.com', 'Meron Getachew', 'DEPARTMENT_MANAGER', 'Finance', TRUE)
) AS sample(email, full_name, role_name, department_name, active)
JOIN roles r ON r.name = sample.role_name
JOIN departments d ON d.name = sample.department_name
WHERE NOT EXISTS (
    SELECT 1
    FROM users existing
    WHERE existing.email = sample.email
);
