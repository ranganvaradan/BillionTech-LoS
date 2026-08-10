-- Create role properly
CREATE ROLE los_admin WITH LOGIN PASSWORD 'admin123';

-- Create databases
CREATE DATABASE los_iam;
CREATE DATABASE los_core;
CREATE DATABASE los_enrollment;
CREATE DATABASE los_notification;
CREATE DATABASE los_lms;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE los_iam TO los_admin;
GRANT ALL PRIVILEGES ON DATABASE los_core TO los_admin;
GRANT ALL PRIVILEGES ON DATABASE los_enrollment TO los_admin;
GRANT ALL PRIVILEGES ON DATABASE los_notification TO los_admin;
GRANT ALL PRIVILEGES ON DATABASE los_lms TO los_admin;