-- Demo users: all passwords are Bltest@123 (BCrypt below). NOT for production.
-- Institution name for API: Billionloans Financial Services Pvt Ltd
INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'a1000000-0000-0000-0000-000000000001'::uuid, 'Sahil C', 'sahil@gmail.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'BORROWER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('sahil@gmail.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'a1000000-0000-0000-0000-000000000002'::uuid, 'Mohit C', 'mohit@billionloans.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'CREDIT_MANAGER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('mohit@billionloans.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'a1000000-0000-0000-0000-000000000003'::uuid, 'Naveen K', 'naveen@billionloans.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'CREDIT_OFFICER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('naveen@billionloans.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'a1000000-0000-0000-0000-000000000004'::uuid, 'Raghul S', 'raghul@billionloans.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'CREDIT_OFFICER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('raghul@billionloans.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'a1000000-0000-0000-0000-000000000005'::uuid, 'Saseendran K', 'sasi@billionloans.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'SALES_OFFICER'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('sasi@billionloans.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'a1000000-0000-0000-0000-000000000006'::uuid, 'Yallappa K', 'yalaappa@billionloans.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'ACCOUNTS'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('yalaappa@billionloans.com'));

INSERT INTO los_users (id, name, email, mobile, active, password_hash, primary_los_role)
SELECT 'a1000000-0000-0000-0000-000000000007'::uuid, 'Alwyn V', 'alwyn@billionloans.com', NULL, true,
       '$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa', 'ADMINISTRATOR'
WHERE NOT EXISTS (SELECT 1 FROM los_users u WHERE lower(u.email) = lower('alwyn@billionloans.com'));

