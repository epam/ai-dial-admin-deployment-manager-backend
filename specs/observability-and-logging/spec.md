# Observability and Logging

## Purpose
This spec describes the logging and observability infrastructure — structured logging via Log4j2, OpenTelemetry integration for distributed tracing, and automatic method execution logging via AOP.

Status: **Implemented**

## Key Terms
- **traceparent**: W3C Trace Context identifier propagated through requests and included in error responses and log entries. Format: `00-{trace-id}-{span-id}-{trace-flags}` (e.g. `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`).
- **OpenTelemetry**: Observability framework used for exporting logs and traces to external platforms.
- **`@LogExecution`**: Custom AOP annotation that enables automatic method invocation logging for all Spring components.
- **`CustomizableTraceInterceptor`**: Spring AOP interceptor wired in `LogConfiguration` to log method entry, exit, and exception events for all beans matched by a configurable AspectJ expression.
- **`CorrelationIdInterceptor`**: Spring `HandlerInterceptor` that reads the W3C `traceparent` from the OpenTelemetry context and sets it as an HTTP response header on every request.

## Requirements

### Requirement: W3C Trace Context propagated across requests
The system SHALL extract and propagate the W3C Trace Context (`traceparent` header) from incoming requests and include it in all log entries and error responses.

Status: **Implemented**

#### Scenario: Traceparent in error response
- **WHEN** any error occurs during request processing
- **THEN** the `traceparent` value (format: `00-{trace-id}-{span-id}-{trace-flags}`) is included in the `ErrorView` response body

#### Scenario: Traceparent as response header
- **WHEN** any HTTP request is processed
- **THEN** `CorrelationIdInterceptor` sets the `traceparent` response header from the current OpenTelemetry trace context

#### Scenario: Traceparent in log entries
- **WHEN** a request is processed
- **THEN** log entries produced during that request include the trace context for correlation

### Requirement: OpenTelemetry log export
The system SHALL integrate with OpenTelemetry to export log entries with trace context attached, enabling correlation in external observability platforms.

Status: **Implemented**

#### Scenario: Log export with trace context
- **WHEN** the application is configured with an OpenTelemetry collector endpoint
- **THEN** log entries are exported to the collector with `traceparent` attached via the OTel Log4j2 appender

### Requirement: Structured logging via Log4j2
The system SHALL use Log4j2 for all application logging with structured output suitable for log aggregation systems.

Status: **Implemented**

#### Scenario: Structured log output
- **WHEN** any component logs a message
- **THEN** the log entry follows the configured Log4j2 pattern (timestamp, level, logger, trace context, message)

#### Scenario: Spring Boot logging bridge excluded
- **WHEN** the application starts
- **THEN** the Spring Boot default Logback starter is excluded and Log4j2 is used exclusively (`spring-boot-starter-log4j2` with `log4j-slf4j2-impl`)

### Requirement: Automatic method execution logging via @LogExecution
All Spring-managed components annotated with `@LogExecution` SHALL have their method invocations automatically logged (entry, exit, and exceptions) via AOP through `CustomizableTraceInterceptor`.

Status: **Implemented**

#### Scenario: Method invocation logged
- **WHEN** a method is called on a component annotated with `@LogExecution`
- **THEN** the invocation is logged with method name and execution outcome (success or exception) via the configured `CustomizableTraceInterceptor` messages

#### Scenario: All Spring components must carry @LogExecution
- **WHEN** a new Spring component class is created (`@RestController`, `@Service`, `@Repository`, `@Component`)
- **THEN** it SHALL be annotated with `@LogExecution` at the class level
- **NOTE**: Precise exclusions and known legacy gaps are codified in `ArchitectureTest` and enforced automatically on every build.

#### Scenario: CustomizableTraceInterceptor conditionally enabled
- **WHEN** `app.customizable-trace-interceptor.enabled` is set to `true`
- **THEN** `LogConfiguration` wires a `CustomizableTraceInterceptor` bean with configurable enter/exit/exception message templates and applies it via an AspectJ expression pointcut advisor

## Implementation Notes
- Logging configuration package: `com.epam.aidial.deployment.manager.configuration.logging`
  - `@LogExecution` annotation: `com.epam.aidial.deployment.manager.configuration.logging.LogExecution`
  - `LogConfiguration`: wires `CustomizableTraceInterceptor` and its pointcut advisor (conditional on `app.customizable-trace-interceptor.enabled`)
  - `CustomizableTraceInterceptorProperties`: configures enter/exit/exception message patterns for the trace interceptor
  - `CorrelationIdInterceptor`: sets `traceparent` response header using `TraceContextUtils.formatTraceParent()`
  - `WebMvcConfig`: registers `CorrelationIdInterceptor` with Spring MVC
  - `TomcatFactoryCustomizer`: Tomcat-level customizations for log integration
- Trace context utility: `com.epam.aidial.deployment.manager.utils.TraceContextUtils` — formats `traceparent` from OTel `Span.current()`
- Log4j2 core version: 2.25.4 (`log4j-core`, `log4j-slf4j2-impl`, `log4j-jul`)
- OTel Log4j2 appender: `opentelemetry-log4j-appender-2.17` (version aligned with the Boot-BOM-managed OpenTelemetry API); installed programmatically by `OpenTelemetryLogAppenderConfiguration` because the official starter does not auto-install it; buffers up to 1000 pre-install startup events (`numLogsCapturedBeforeOtelInstall` in `log4j2.xml`) so they are not lost before the Spring context wires the SDK
- OTel Spring Boot starter: official `org.springframework.boot:spring-boot-starter-opentelemetry` (Spring Boot 4); telemetry is configured via `management.*` properties (see `docs/configuration.md` § OpenTelemetry Configuration) and OTLP export is disabled by default via `OTEL_EXPORT_ENABLED=false`
- Log configuration files: `log-config/` (Log4j2 XML configuration)
- Related specs: `api-conventions`; `deployment-metrics` (workload-engine metrics snapshot — enables the trace→deployment-metrics pivot for model deployments)
