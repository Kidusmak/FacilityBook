-- Demo data for local development and the internship demo.
-- All demo accounts use the password: Password123!
INSERT INTO departments (name) VALUES
    ('Engineering'),
    ('Human Resources'),
    ('Facilities'),
    ('IT'),
    ('Finance');

INSERT INTO users (email, password_hash, full_name, role_id, department_id) VALUES
    ('admin@facilitybook.local',
     '$2b$10$wuB4kODw9wVMqdTIbFMkp.iMrS/rkVQU4IadNukr.uJn.QcZdAXMu',
     'System Administrator',
     (SELECT id FROM roles WHERE name = 'ADMIN'),
     NULL),
    ('employee@facilitybook.local',
     '$2b$10$wuB4kODw9wVMqdTIbFMkp.iMrS/rkVQU4IadNukr.uJn.QcZdAXMu',
     'Emma Employee',
     (SELECT id FROM roles WHERE name = 'EMPLOYEE'),
     (SELECT id FROM departments WHERE name = 'Engineering')),
    ('facility@facilitybook.local',
     '$2b$10$wuB4kODw9wVMqdTIbFMkp.iMrS/rkVQU4IadNukr.uJn.QcZdAXMu',
     'Frank Facility',
     (SELECT id FROM roles WHERE name = 'FACILITY_MANAGER'),
     (SELECT id FROM departments WHERE name = 'Facilities')),
    ('deptmanager@facilitybook.local',
     '$2b$10$wuB4kODw9wVMqdTIbFMkp.iMrS/rkVQU4IadNukr.uJn.QcZdAXMu',
     'Dana Manager',
     (SELECT id FROM roles WHERE name = 'DEPARTMENT_MANAGER'),
     (SELECT id FROM departments WHERE name = 'Engineering')),
    ('operator@facilitybook.local',
     '$2b$10$wuB4kODw9wVMqdTIbFMkp.iMrS/rkVQU4IadNukr.uJn.QcZdAXMu',
     'Olivia Operator',
     (SELECT id FROM roles WHERE name = 'RESOURCE_OPERATOR'),
     (SELECT id FROM departments WHERE name = 'Facilities'));
