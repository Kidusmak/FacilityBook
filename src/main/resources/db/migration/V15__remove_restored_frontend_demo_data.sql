-- V14 was applied to some databases, so preserve its Flyway history and undo
-- only the sample rows it inserted. Keep any accounts that have real activity.
DELETE FROM users user_row
WHERE user_row.email IN (
        'abebe@example.com',
        'sara@example.com',
        'dawit@example.com',
        'martha@example.com',
        'daniel@example.com',
        'hana@example.com',
        'samuel@example.com',
        'meron@example.com'
    )
  AND user_row.password_hash =
      '$2b$10$wuB4kODw9wVMqdTIbFMkp.iMrS/rkVQU4IadNukr.uJn.QcZdAXMu'
  AND user_row.created_at BETWEEN
      (SELECT installed_on - INTERVAL '1 hour'
       FROM flyway_schema_history WHERE version = '14')
      AND
      (SELECT installed_on + INTERVAL '1 hour'
       FROM flyway_schema_history WHERE version = '14')
  AND NOT EXISTS (
      SELECT 1 FROM reservations reservation
      WHERE reservation.requester_id = user_row.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM reservation_approvals approval
      WHERE approval.approver_id = user_row.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM maintenance_records maintenance
      WHERE maintenance.reported_by = user_row.id
  );

DELETE FROM departments department
WHERE department.name IN ('Administration', 'Information Technology', 'Operations')
  AND department.created_at BETWEEN
      (SELECT installed_on - INTERVAL '1 hour'
       FROM flyway_schema_history WHERE version = '14')
      AND
      (SELECT installed_on + INTERVAL '1 hour'
       FROM flyway_schema_history WHERE version = '14')
  AND NOT EXISTS (
      SELECT 1 FROM users user_row
      WHERE user_row.department_id = department.id
  );
