# Observability and Logging

## Purpose
This spec describes the logging and observability infrastructure — structured logging via Log4j2, OpenTelemetry integration for distributed tracing, and automatic method execution logging via AOP.

Status: **Implemented**

## Key Terms
- **traceparent**: W3C Trace Context identifier propagated through requests and included in error responses and log entries.
- **OpenTelemetry**: Observability framework used for exporting logs and traces to external platforms.
- **`@LogExecution`**: Custom AOP annotation that enables automatic method invocation logging for all Spring components.

## Requirements

### Requirement: W3C Trace Context propagated across requests
The system SHALL extract and propagate the W3C Trace Context (`traceparent` header) from incoming requests and include it in all log entries and error responses.

Status: **Implemented**

#### Scenario: Traceparent in error response
- **WHEN** any error occurs during request processing
- **THEN** the `traceparent` value (format: `00-{trace-id}-{span-id}-{trace-flags}`) is included in the `ErrorView` response body

#### Scenario: Traceparent in log entries
- **WHEN** a request is processed
- **THEN** log entries produced during that request include the trace context for correlation

### Requirement: OpenTelemetry log export
The system SHALL integrate with OpenTelemetry to export log entries with trace context attached, enabling correlation in external observability platforms.

Status: **Implemented**

#### Scenario: Log export with trace context
- **WHEN** the application is configured with an OpenTelemetry collector endpoint
- **THEN** log entries are exported to the collector with `traceparent` attached

### Requirement: Structured logging via Log4j2
The system SHALL use Log4j2 for all application logging with structured output suitable for log aggregation systems.

Status: **Implemented**

#### Scenario: Structured log output
- **WHEN** any component logs a message
- **THEN** the log entry follows the configured Log4j2 pattern (timestamp, level, logger, trace context, message)

### Requirement: Automatic method execution logging via @LogExecution
All Spring-managed components annotated with `@LogExecution` SHALL have their method invocations automatically logged (entry, exit, and exceptions) via AOP.

Status: **Implemented**

#### Scenario: Method invocation logged
- **WHEN** a method is called on a component annotated with `@LogExecution`
- **THEN** the invocation is logged with method name and execution outcome (success or exception)

#### Scenario: All Spring components must carry @LogExecution
- **WHEN** a new Spring component class is created (`@RestController`, `@Service`, `@Repository`, `@Component`, `@Configuration`)
- **THEN** it SHALL be annotated with `@LogExecution` at the class level

## Implementation Notes
- Logging config: `com.epam.aidial.deployment.manager.configuration.logging.*`
- `@LogExecution` annotation: `com.epam.aidial.deployment.manager.configuration.logging.LogExecution`
- Log4j2 + OpenTelemetry appender: version 2.25.3
- Log configuration files: `log-config/`
- Related spec: `api-conventions`
