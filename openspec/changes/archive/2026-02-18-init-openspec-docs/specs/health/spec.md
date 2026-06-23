# Health

## Purpose
This spec describes the health endpoint — a simple readiness/liveness check for the application.

Status: **Implemented**

## Key Terms
- **Health check**: A lightweight endpoint that reports whether the application is operational and ready to serve traffic.

## ADDED Requirements

### Requirement: Health endpoint reports application status
The system SHALL expose a health check endpoint at `GET /api/v1/health` that returns `{"status": "UP"}` when the application is operational and ready to serve traffic. The endpoint is publicly accessible without authentication.

Status: **Implemented**

#### Scenario: Application healthy
- **WHEN** `GET /api/v1/health` is called on a fully started, healthy application
- **THEN** the response is HTTP 200 with body `{"status": "UP"}`

#### Scenario: Application not yet ready
- **WHEN** the health endpoint is called before the application has fully started
- **THEN** the response indicates the application is not ready (non-200 or appropriate status body)

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.HealthController`
- Path: `GET /api/v1/health`
- Response body: `{"status": "UP"}`
- Auth: Public — exempt from authentication in all security modes
