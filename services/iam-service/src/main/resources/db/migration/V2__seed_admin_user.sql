-- V2__seed_admin_user.sql
-- Default admin user: admin / Admin@LOS2026
-- Password hash is BCrypt(12) of "Admin@LOS2026"

INSERT INTO users (id, username, email, password_hash, first_name, last_name, enabled)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'admin',
    'admin@los.local',
    '$2a$12$ZkAULKdwPD/axPT2Uoxq1eRYjulbz3l58hTjWxLYjmYfNSfHub7H6',
    'System',
    'Admin',
    TRUE
);

INSERT INTO user_roles (user_id, role) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'ADMIN'),
    ('a0000000-0000-0000-0000-000000000001', 'CREDIT_MANAGER');
