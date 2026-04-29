-- Create databases for LOS Platform
CREATE DATABASE los_iam;
CREATE DATABASE los_core;
CREATE DATABASE los_enrollment;
CREATE DATABASE los_notification;

CREATE DATABASE los_bankstmt;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE los_iam TO los_admin;
GRANT ALL PRIVILEGES ON DATABASE los_core TO los_admin;
GRANT ALL PRIVILEGES ON DATABASE los_enrollment TO los_admin;
GRANT ALL PRIVILEGES ON DATABASE los_notification TO los_admin;
GRANT ALL PRIVILEGES ON DATABASE los_bankstmt TO los_admin;
