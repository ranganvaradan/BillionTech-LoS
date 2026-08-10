-- Program Lending Platform — Database Initialization
-- Creates schemas for each microservice (schema-per-service isolation)

-- IAM Service Schema
CREATE SCHEMA IF NOT EXISTS plp_iam;

-- Program Service Schema
CREATE SCHEMA IF NOT EXISTS plp_program;

-- Lending Service Schema
CREATE SCHEMA IF NOT EXISTS plp_lending;

-- Integration Service Schema
CREATE SCHEMA IF NOT EXISTS plp_integration;

-- Notification Service Schema
CREATE SCHEMA IF NOT EXISTS plp_notification;

-- Report Service Schema
CREATE SCHEMA IF NOT EXISTS plp_report;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA plp_iam TO plp_admin;
GRANT ALL PRIVILEGES ON SCHEMA plp_program TO plp_admin;
GRANT ALL PRIVILEGES ON SCHEMA plp_lending TO plp_admin;
GRANT ALL PRIVILEGES ON SCHEMA plp_integration TO plp_admin;
GRANT ALL PRIVILEGES ON SCHEMA plp_notification TO plp_admin;
GRANT ALL PRIVILEGES ON SCHEMA plp_report TO plp_admin;
