-- Optimo sandbox users (password for all: Bltest@123).
-- BCrypt: $2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'd2000000-0000-0000-0000-000000000001'::uuid, 'Admin', 'admin@optimo.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'ADMINISTRATOR'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('admin@optimo.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'd2000000-0000-0000-0000-000000000002'::uuid, 'Credit Manager', 'creditmanager@optimo.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'CREDIT_MANAGER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('creditmanager@optimo.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'd2000000-0000-0000-0000-000000000003'::uuid, 'Credit Officer', 'creditofficer@optimo.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'CREDIT_OFFICER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('creditofficer@optimo.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'd2000000-0000-0000-0000-000000000004'::uuid, 'Credit Officer 2', 'creditofficer2@optimo.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'CREDIT_OFFICER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('creditofficer2@optimo.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'd2000000-0000-0000-0000-000000000005'::uuid, 'Sales', 'sales@optimo.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'SALES_OFFICER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('sales@optimo.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'd2000000-0000-0000-0000-000000000006'::uuid, 'Accounts', 'accounts@optimo.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'ACCOUNTS'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('accounts@optimo.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'd2000000-0000-0000-0000-000000000007'::uuid, 'Borrower', 'borrower@optimo.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'BORROWER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('borrower@optimo.com'));
