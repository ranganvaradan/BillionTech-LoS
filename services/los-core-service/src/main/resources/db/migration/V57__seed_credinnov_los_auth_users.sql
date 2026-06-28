-- Credinnov sandbox users (password for all: Bltest@123).
-- BCrypt: $2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa

UPDATE los_users
SET active = false
WHERE lower(email) LIKE '%@billionloans.com'
   OR lower(email) = lower('sahil@gmail.com');

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'c1000000-0000-0000-0000-000000000001'::uuid, 'Admin', 'admin@credinnov.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'ADMINISTRATOR'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('admin@credinnov.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'c1000000-0000-0000-0000-000000000002'::uuid, 'Credit Manager', 'creditmanager@credinnov.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'CREDIT_MANAGER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('creditmanager@credinnov.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'c1000000-0000-0000-0000-000000000003'::uuid, 'Credit Officer', 'creditofficer@credinnov.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'CREDIT_OFFICER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('creditofficer@credinnov.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'c1000000-0000-0000-0000-000000000004'::uuid, 'Credit Officer 2', 'creditofficer2@credinnov.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'CREDIT_OFFICER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('creditofficer2@credinnov.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'c1000000-0000-0000-0000-000000000005'::uuid, 'Sales', 'sales@credinnov.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'SALES_OFFICER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('sales@credinnov.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'c1000000-0000-0000-0000-000000000006'::uuid, 'Accounts', 'accounts@credinnov.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'ACCOUNTS'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('accounts@credinnov.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'c1000000-0000-0000-0000-000000000007'::uuid, 'Borrower', 'borrower@credinnov.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'BORROWER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('borrower@credinnov.com'));
