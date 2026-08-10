-- Re-enable Billionloans team access (V57 deactivated @billionloans.com for Credinnov sandbox).
-- Password for seeded users: Bltest@123
-- BCrypt: $2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa

UPDATE los_users
SET active = true
WHERE lower(email) LIKE '%@billionloans.com';

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'b1000000-0000-0000-0000-000000000001'::uuid, 'LOS Admin', 'admin@billionloans.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'ADMINISTRATOR'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('admin@billionloans.com'));
