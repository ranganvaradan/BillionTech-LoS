-- Optimo Relationship Manager sandbox user (password: Bltest@123 — same hash as V66 Optimo seeds).
-- Mirrors V75 rm@credinnov.com for the Optimo tenant.

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'd2000000-0000-0000-0000-000000000008'::uuid, 'Relationship Manager', 'rm@optimo.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'RELATIONSHIP_MANAGER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('rm@optimo.com'));
