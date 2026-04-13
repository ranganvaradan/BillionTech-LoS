# LOS Platform v2.0

Modernized, API-first Loan Origination System built with Spring Boot 3.3, Java 21, PostgreSQL 16, Redis 7.2, and RabbitMQ 3.13.

## Architecture

```
                    Nginx (TLS + Static)
                           │
                    API Gateway (8080)
                    ┌──────┼──────────┐
                    │      │          │
               IAM (8081)  │    Enrollment (8082)
                           │
                    LOS Core (8083)
                           │
                  ┌────────┼────────┐
                  │        │        │
           Notification  LMS      Discovery
             (8084)    Adapter     (8761)
                       (8085)
```

## Quick Start

```bash
# Start infrastructure
docker compose -f docker-compose.infra.yml up -d

# Build all services
./mvnw clean package -DskipTests

# Start all services
docker compose up -d
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| Discovery (Eureka) | 8761 | Service registry |
| API Gateway | 8080 | Request routing, JWT validation, rate limiting |
| IAM Service | 8081 | Authentication, authorization, RBAC, 2FA |
| Enrollment Service | 8082 | Customer registration, OTP, consent |
| LOS Core Service | 8083 | Loan applications, KYC, workflow, eSign, transactions |
| Notification Service | 8084 | SMS, email, WhatsApp notifications |
| LMS Adapter | 8085 | Loan Management System integration |

## Infrastructure

| Component | Port | Purpose |
|-----------|------|---------|
| PostgreSQL 16 | 5432 | Primary database |
| Redis 7.2 | 6379 | Cache, OTP sessions, rate limiting |
| RabbitMQ 3.13 | 5672 / 15672 | Message queue / Management UI |
| MinIO | 9000 / 9001 | S3-compatible object storage |
| Prometheus | 9090 | Metrics collection |
| Grafana | 3002 | Monitoring dashboards |

## Tech Stack

- **Java 21** with Virtual Threads
- **Spring Boot 3.3.5** + Spring Cloud 2023.0.3
- **PostgreSQL 16** with JSONB + Flyway migrations
- **Redis 7.2** for caching and session management
- **RabbitMQ 3.13** for event-driven notifications
- **MinIO** for document storage (S3-compatible)
- **SpringDoc OpenAPI 3.1** for API documentation
