-- Demo buildings, resources and operating hours for a clean local/demo run.
-- No reservations are seeded so every demo starts from an empty booking calendar.
INSERT INTO buildings (name, address) VALUES
    ('Building 1', '1 Campus Way'),
    ('Building 2', '2 Campus Way'),
    ('IT Department', '3 Campus Way')
    ON CONFLICT (name) DO NOTHING;

INSERT INTO resources (name, description, category_id, building_id, floor, location_detail,
                       capacity, features, status)
VALUES
    ('Conference Room A', 'Main meeting room with video conferencing',
     (SELECT id FROM resource_categories WHERE name = 'Meeting Room'),
     (SELECT id FROM buildings WHERE name = 'Building 1'),
     '1', 'East wing', 30, 'Projector, Whiteboard, Video call', 'ACTIVE'),
    ('Training Room B', 'Room used for training and workshops',
     (SELECT id FROM resource_categories WHERE name = 'Training Room'),
     (SELECT id FROM buildings WHERE name = 'Building 2'),
     '2', 'West wing', 50, 'Projector, Whiteboard, Sound system', 'ACTIVE'),
    ('Meeting Room C', 'Small meeting room',
     (SELECT id FROM resource_categories WHERE name = 'Meeting Room'),
     (SELECT id FROM buildings WHERE name = 'Building 1'),
     '3', 'North wing', 12, 'Whiteboard, Video call', 'ACTIVE'),
    ('Conference Hall', 'Large hall for events and all-hands meetings',
     (SELECT id FROM resource_categories WHERE name = 'Conference Hall'),
     (SELECT id FROM buildings WHERE name = 'Building 2'),
     '1', 'Main auditorium', 150, 'Stage, PA system, Projector', 'ACTIVE'),
    ('Projector 01', 'Portable full-HD projector',
     (SELECT id FROM resource_categories WHERE name = 'Equipment'),
     (SELECT id FROM buildings WHERE name = 'IT Department'),
     NULL, 'IT store room', NULL, 'HDMI, Carry case', 'ACTIVE'),
    ('Company Van 01', 'Eight-seat company van',
     (SELECT id FROM resource_categories WHERE name = 'Vehicle'),
     NULL, NULL, 'Fleet parking', 8, 'GPS, Fuel card', 'ACTIVE');

-- Weekday operating hours for rooms and the hall (08:00-18:00).
INSERT INTO operating_hours (resource_id, day_of_week, open_time, close_time)
SELECT r.id, d.day_of_week, TIME '08:00', TIME '18:00'
FROM resources r
CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS d(day_of_week)
WHERE r.name IN ('Conference Room A', 'Training Room B', 'Meeting Room C', 'Conference Hall')
ON CONFLICT (resource_id, day_of_week) DO NOTHING;

-- Equipment can be collected during IT working hours.
INSERT INTO operating_hours (resource_id, day_of_week, open_time, close_time)
SELECT r.id, d.day_of_week, TIME '09:00', TIME '17:00'
FROM resources r
CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS d(day_of_week)
WHERE r.name = 'Projector 01'
ON CONFLICT (resource_id, day_of_week) DO NOTHING;

-- The van is available Monday to Saturday.
INSERT INTO operating_hours (resource_id, day_of_week, open_time, close_time)
SELECT r.id, d.day_of_week, TIME '07:00', TIME '20:00'
FROM resources r
CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6)) AS d(day_of_week)
WHERE r.name = 'Company Van 01'
ON CONFLICT (resource_id, day_of_week) DO NOTHING;
