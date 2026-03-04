<!-- Sync Impact Report
Version change: 1.0.0 → 1.1.0 (MINOR: new sections + Key Patterns expansion)
Added sections: Multi-Vendor Database Pattern, Tooling Commands
Modified sections: Key Patterns (expanded @LogExecution known gaps, MapStruct settings, Lombok patterns, Records; fixed migration paths)
Fixed: Key Patterns table listed src/main/java/db/migration/ for SQL files — corrected to src/main/resources/db/migration/; java-based migrations path clarified
Templates requiring updates:
  ✅ .specify/memory/constitution.md (this file)
  ✅ No [PLACEHOLDER] tokens remain
Follow-up TODOs: none
-->

# DIAL Deployment Manager Backend Constitution

## Tech Stack

The project MUST use the following canonical versions. Any upgrade MUST go through a PR with a security or feature rationale.

- **Runtime**: Java 21, Spring Boot 3.5.10, Gradle 8.13
- **Code generation**: Lombok 8.10 (freefair plugin), MapStruct 1.6.0
- **Database migrations**: Flyway 11.14.0 (postgresql + sqlserver modules)
- **Kubernetes**: Fabric8 Kubernetes Client 7.5.2, Fabric8 Knative Client 7.5.2, io.kubernetes:client-java 22.0.0
- **Serialization**: Jackson 2.21.1 (enforced via buildscript constraint)
- **Logging**: Log4j2 2.25.3 (log4j-core), SLF4J 2 bridge (log4j-slf4j2-impl); spring-boot-starter-logging MUST be excluded globally
- **Observability**: OpenTelemetry SDK + OTLP exporter, opentelemetry-spring-boot-starter 2.12.0, Micrometer + Prometheus
- **Caching**: Caffeine 3.2.3 via spring-boot-starter-cache
- **Distributed locking**: ShedLock 6.3.0 (JDBC provider)
- **API docs**: SpringDoc OpenAPI 2.8.5
- **Security**: Spring Security + OAuth2 Resource Server; Azure Identity 1.18.0; supported providers: azure, keycloak, auth0, okta, cognito
- **Databases supported**: H2 2.3.232 (dev/test), PostgreSQL 42.7.8, SQL Server 13.2.1 (mssql-jdbc)
- **Code quality**: Checkstyle 10.21.4 (Google Java Style profile), `-Werror` on all Java compilation

## Architecture Principles

### I. Strict Layered Architecture

The codebase MUST follow this dependency direction with no exceptions:

```
web (controller / handler / dto / mapper)
  → service (business logic, @Transactional)
    → dao (entity / repository / jpa / mapper)
    → kubernetes (informer / knative / nim / kserve / event)
```

- `web` layer MUST NOT call `dao` or `kubernetes` directly.
- `service` layer MUST NOT be called by `dao` or `kubernetes`.
- `kubernetes` package MUST be the only place Fabric8/io.kubernetes API types appear in non-configuration code.
- Cross-layer shortcutting is a blocking PR review comment.

### II. Transactional Discipline

- `@Transactional` MUST only appear on `service`-layer classes or their methods.
- Controllers and DAOs MUST NOT carry `@Transactional`.
- Propagation and isolation overrides require an explanatory comment.

### III. Kubernetes Isolation

- All calls that create, update, delete, or read Kubernetes resources MUST live in the `kubernetes/` package.
- Services MUST NOT import `io.fabric8.*` or `io.kubernetes.*` client types (only domain models are allowed to cross this boundary).
- Fabric8 informers are the canonical mechanism for watching K8s state changes; polling loops are forbidden.

### IV. Observability First

- `@LogExecution` MUST be placed on every Spring component class (`@Service`, `@Component`, `@Controller`, `@RestController`).
- All telemetry signals (traces, metrics, logs) MUST flow through OpenTelemetry; direct log statements in business logic are acceptable only for debug-level context.
- Prometheus metrics are exposed via Actuator; custom `MeterRegistry` registrations follow existing patterns in `configuration/`.

### V. Security by Configuration

- Default security mode is `none`; production deployments MUST set `CONFIG_REST_SECURITY_MODE` to `oidc` or `basic`.
- Sensitive environment variables MUST be converted to Kubernetes Secrets automatically by the pipeline; they MUST NOT be stored in ConfigMaps or committed to git.
- JWT validation is multi-provider: each provider configured independently under `providers.{name}.*`.

## Naming Conventions

| Artifact | Pattern | Example |
|---|---|---|
| Request/response DTO | `*Dto`, `*RequestDto` | `DeploymentDto`, `CreateDeploymentRequestDto` |
| Web layer mapper (MapStruct) | `*DtoMapper` | `DeploymentDtoMapper` |
| Persistence mapper (MapStruct) | `Persistence*Mapper` | `PersistenceDeploymentMapper` |
| JPA entity | `*Entity` | `DeploymentEntity` |
| Spring Data JPA repository | `*JpaRepository` | `DeploymentJpaRepository` |
| Domain/DAO repository wrapper | `*Repository` | `DeploymentRepository` |
| Service | `*Service` | `DeploymentService` |
| Controller | `*Controller` | `DeploymentController` |
| Functional / integration test | `*FunctionalTest` | `DeploymentFunctionalTest` |
| Unit test | `*Test` | `DeploymentServiceTest` |

Package names MUST be all-lowercase with no underscores (enforced by Checkstyle `PackageName`).

## Code Style

- **Style guide**: Google Java Style, enforced by Checkstyle 10.21.4.
- **Line length**: 180 characters maximum (enforced).
- **Compiler**: `-Werror` — all warnings are errors; no suppression without justification.
- **Imports**: No wildcard (`*`) imports (enforced). Import order: third-party → standard Java → static, alphabetically within groups.
- **FQNs in method bodies**: Forbidden; always use a proper import.
- **Local variables**: SHOULD use `var` where the type is obvious from the right-hand side.
- **Braces**: K&R style; mandatory for all control flow statements.
- **Indentation**: 4 spaces; no tabs.
- **One statement per line** and **one top-level class per file** enforced.

## API Conventions

- **Public API base path**: `/api/v1/`
- **Internal API base path**: `/api/internal/v1/`
- **Error response schema**: `ErrorView` — MUST include `message`, `code`, and `traceparent` (from OTel span context).
- **OpenAPI annotations**: Every endpoint method MUST carry `@Operation` (SpringDoc) with `summary` and relevant `@ApiResponse` annotations.
- **Pagination**: List endpoints returning potentially large datasets MUST support pagination via Spring's `Pageable`.
- **SSE endpoints**: Real-time status updates (builds, deployments) MUST use `SseEmitter` and follow existing patterns in `web/handler/`.
- **Health check**: Standard Spring Boot Actuator at `/actuator/health`; custom health indicators are allowed in `configuration/`.

## Testing Conventions

- **Test method naming**: `shouldDoX()` for happy paths, `shouldFailDoX_whenY()` for failure scenarios.
- **Database selection**: Controlled by `DATASOURCE_VENDOR` env var; Testcontainers for Postgres and SQL Server in CI, H2 for lightweight local runs.
- **Test infrastructure**: Testcontainers 1.21.3; `@Testcontainers` + `@Container` for container lifecycle.
- **Assertions**: AssertJ preferred (`assertThat(...)`); raw JUnit `assertEquals` is discouraged.
- **Functional tests**: MUST extend or mirror `PostgresFunctionalTests` / `SqlServerFunctionalTests` base patterns; cover both supported vendors where SQL behavior may differ.
- **Security tests**: Use `spring-security-test` for authenticated endpoint tests; JWT tokens generated with `io.jsonwebtoken:jjwt`.
- **No mocking of K8s calls in functional tests**: Use Testcontainers or in-memory stubs defined in `src/test/`.

## Key Patterns

**`@LogExecution`** — Class-level annotation MUST appear on every Spring component (`@RestController`,
`@Service`, `@Repository`, `@Component`, `@Configuration`). Import:
`com.epam.aidial.deployment.manager.configuration.logging.LogExecution`. Wired via
`CustomizableTraceInterceptor` in `configuration/logging/`. Known gaps in existing code (legacy —
do NOT treat as a model): `TopicController`, `GlobalDomainWhitelistController`, `HealthController`,
`McpController`, `DisposableResourceController`. All new code MUST include it without exception.

**MapStruct** — All mapper interfaces MUST declare:
```java
@Mapper(componentModel = "spring",
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
```
Web mappers (`*DtoMapper`) live in `web/mapper/`; DAO mappers (`Persistence*Mapper`) in `dao/mapper/`;
service-level mappers (`*Mapper`) in `mapper/`.

**Lombok** — Standard annotations: `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`
for mutable domain objects and entities; `@Slf4j` for logging (never declare a `Logger` field manually).
`@Value` and Java `record` types are preferred for simple immutable DTOs (e.g., request/response
records where no inheritance is needed).

| Pattern | Mechanism | Location |
|---|---|---|
| Execution logging | `@LogExecution` AOP (see above) | `configuration/logging/` |
| Response caching | Caffeine + `@Cacheable` / `@CacheEvict` | `configuration/`, service layer |
| Distributed job locking | ShedLock `@SchedulerLock` | `service/` scheduled components |
| K8s state watching | Fabric8 informers + handler registrations | `kubernetes/informer/` |
| SQL schema migrations | Flyway `.sql` files per vendor | `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/` |
| Java data migrations | Flyway `.java` callbacks per vendor | `src/main/java/db/migration/{H2,POSTGRES,MS_SQL_SERVER,common}/` |
| JPA DDL | `ddl-auto: validate` only; Flyway owns schema | `configuration/datasource/` |
| Image build pipeline | Multi-step `service/pipeline/step/` chain | `service/pipeline/` |
| Resource lifecycle/cleanup | `cleanup/` package with `@Component` registrations | `cleanup/` |

## Multi-Vendor Database Pattern

Database vendor is selected at runtime via the `DATASOURCE_VENDOR` environment variable:
`H2` (default for local/test) | `POSTGRES` | `MS_SQL_SERVER`.

**SQL migrations** — `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/`
- Naming: `V{major}.{minor}__{Description}.sql` — e.g. `V1.25__AddAuthorColumnToTables.sql`
- Dot separator between major and minor (standard Flyway format)

**Java migrations** — `src/main/java/db/migration/{H2,POSTGRES,MS_SQL_SERVER,common}/`
- Used for complex data migrations that require Java logic (e.g. JSON transformations)
- Naming: `V{major}_{minor}__{Description}.java` — underscore replaces dot (dots invalid in Java class names)
- `common/` subfolder applies to all vendors

**Flyway configuration**:
- `baseline-on-migrate: true`
- `baseline-version: 1.1`
- JPA `ddl-auto: validate` — Flyway owns schema; Hibernate MUST NOT create or alter tables

When adding a migration, create the corresponding file in all applicable vendor directories
(H2, POSTGRES, MS_SQL_SERVER) unless the migration is vendor-specific by design.

## Anti-Patterns

The following MUST NOT appear in new code. PRs introducing these patterns MUST be rejected:

1. **Business logic in entities** — `*Entity` classes are pure data holders; no service calls, computed state, or Lombok `@Builder` default methods with side effects.
2. **Silent exception swallowing** — Every `catch` block MUST either rethrow, log with context, or explicitly document why suppression is safe.
3. **Generic `Exception` catch** — Catch specific exception types. `catch (Exception e)` is allowed only in top-level exception handlers (`DefaultExceptionHandler`).
4. **`@Transactional` on controllers or DAOs** — Violates Transactional Discipline (Principle II).
5. **Kubernetes API calls from the service layer** — Violates Kubernetes Isolation (Principle III).
6. **Wildcard imports** — Violates Code Style; blocked by Checkstyle.
7. **Direct `System.out.println` or `e.printStackTrace()`** — Use SLF4J logger via Log4j2.
8. **Hard-coded credentials or secrets** — All secrets via environment variables or Kubernetes Secrets.
9. **Polling loops for K8s state** — Use Fabric8 informers.
10. **`ddl-auto` values other than `validate`** — Flyway owns schema; JPA must not create or update tables.

## Tooling Commands

| Command | Purpose |
|---|---|
| `./gradlew test` | Run all tests |
| `./gradlew checkstyleMain checkstyleTest` | Run Checkstyle on main and test sources |
| `./gradlew clean build` | Full clean build (tests + Checkstyle) |
| `./gradlew clean bootJar` | Build executable JAR without running tests |
| `docker build -t deployment-manager .` | Build Docker image (two-stage: `gradle:8.13-jdk21-alpine` → `amazoncorretto:21-alpine`) |

After any code change, always verify with `./gradlew checkstyleMain checkstyleTest` before
considering the task done. A clean `./gradlew clean build` is the gate for PR readiness.

## Governance

This constitution is the authoritative source of architectural truth for the DIAL Deployment Manager Backend. It supersedes any conflicting guidance in PR comments, local convention notes, or informal team agreements.

**Amendment procedure**:
1. Raise a PR with changes to this file.
2. Tag `@team-leads` for review; at least one approval required.
3. Include a migration note if the amendment changes existing code patterns (link to a follow-up issue for affected code).
4. Run `/speckit.constitution` via Claude Code to propagate changes to dependent templates.

**Versioning policy**:
- MAJOR: Removal or redefinition of an existing principle (breaks existing code).
- MINOR: New principle, section, or materially expanded guidance.
- PATCH: Clarifications, wording, typo fixes, non-semantic refinements.

**Compliance review**: All PRs MUST be checked against this constitution. Automated checks (Checkstyle, `-Werror`) enforce code style sections; architectural sections are enforced via PR review.

**Version**: 1.1.0 | **Ratified**: 2026-03-04 | **Last Amended**: 2026-03-04
