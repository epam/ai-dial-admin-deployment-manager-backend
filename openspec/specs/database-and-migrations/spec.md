# Database and Migrations

## Purpose
This spec describes the multi-vendor database strategy and Flyway migration conventions. The application supports three database vendors, selected at runtime, with vendor-specific migration directories.

Status: **Implemented**

## Key Terms
- **DATASOURCE_VENDOR**: Environment variable that selects the active database vendor (`H2`, `POSTGRES`, or `MS_SQL_SERVER`).
- **Flyway**: Database schema migration tool. Migrations are applied on startup per the active vendor.
- **Migration**: A versioned SQL file under the vendor-specific migration path, applied in order by Flyway.
- **JPA/Hibernate**: The ORM layer used for entity persistence. Entities live in `dao/entity/`; JPA repository interfaces in `dao/jpa/`; custom repository wrappers (with domain mapping) in `dao/repository/`; entity-to-domain mappers in `dao/mapper/`.

## Migration Paths per Vendor

| Vendor | Path |
|---|---|
| H2 | `src/main/resources/db/migration/H2/` |
| PostgreSQL | `src/main/resources/db/migration/POSTGRES/` |
| SQL Server | `src/main/resources/db/migration/MS_SQL_SERVER/` |

## Requirements

### Requirement: Multi-vendor database support
The system SHALL support three database vendors selectable at runtime via `DATASOURCE_VENDOR`.

Status: **Implemented**

#### Scenario: H2 (development / embedded)
- **WHEN** `DATASOURCE_VENDOR=H2`
- **THEN** the application starts with an embedded H2 database; no external database required

#### Scenario: PostgreSQL (production)
- **WHEN** `DATASOURCE_VENDOR=POSTGRES`
- **THEN** the application connects to the configured PostgreSQL instance

#### Scenario: SQL Server (alternative production)
- **WHEN** `DATASOURCE_VENDOR=MS_SQL_SERVER`
- **THEN** the application connects to the configured SQL Server instance

### Requirement: Flyway applies schema migrations on startup
The system SHALL use Flyway to apply all pending migrations for the active vendor on startup, in version order.

Status: **Implemented**

#### Scenario: Migrations applied in order
- **WHEN** the application starts with pending migrations
- **THEN** Flyway applies them in version order before the application becomes ready

#### Scenario: Vendor-specific migration path selected
- **WHEN** a specific vendor is configured
- **THEN** Flyway reads migrations from the corresponding vendor subdirectory only

### Requirement: Migration naming follows versioned convention
Flyway migration files SHALL follow the naming convention `V{major}.{minor}__{Description}.sql` with a double underscore separator.

Status: **Implemented**

#### Scenario: Correctly named migration accepted
- **WHEN** a migration file is named `V1.5__AddDeploymentTable.sql`
- **THEN** Flyway recognizes and applies it at version 1.5

#### Scenario: Incorrectly named migration fails
- **WHEN** a migration file uses a single underscore or missing version prefix
- **THEN** Flyway rejects it with a naming validation error

### Requirement: Spring Data JPA with Hibernate manages entity persistence
The system SHALL use Spring Data JPA with Hibernate as the ORM layer. Repositories extend `JpaRepository`. Business logic SHALL NOT be placed in entity classes.

Status: **Implemented**

#### Scenario: JPA repository operation
- **WHEN** a service calls a JPA repository method
- **THEN** Hibernate translates the operation to the appropriate SQL dialect for the active vendor

## Implementation Notes
- Config property: `DATASOURCE_VENDOR` (`H2` | `POSTGRES` | `MS_SQL_SERVER`)
- Datasource configuration: `com.epam.aidial.deployment.manager.configuration.datasource.*`
- JPA entities: `com.epam.aidial.deployment.manager.dao.entity.*`
- JPA repository interfaces: `com.epam.aidial.deployment.manager.dao.jpa.*` (extend `JpaRepository`)
- Custom repository wrappers: `com.epam.aidial.deployment.manager.dao.repository.*` (delegate to JPA repos, add domain mapping)
- Entity-to-domain mappers: `com.epam.aidial.deployment.manager.dao.mapper.*`
- Schema / config docs: `docs/configuration.md`
