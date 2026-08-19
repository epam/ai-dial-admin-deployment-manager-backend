# SQL migrations

Flyway-managed schema and data migrations, one directory per supported database vendor. Spec: `specs/database-and-migrations/spec.md`. Constitution: `.specify/memory/constitution.md` § Multi-Vendor Database Pattern.

## Naming

- `V{major}.{minor}__{Description}.sql` — dot separator (standard Flyway). Example: `V1.59__AddSomeColumn.sql`.

## Vendor parity

Three subdirectories: `H2/`, `POSTGRES/`, `MS_SQL_SERVER/`. New migrations MUST be added to **all three** unless the change is intentionally vendor-specific (in which case state why in the file or commit message). Mismatched versions across vendors will cause Flyway baseline drift.

## Column types

JSON-bearing columns (entity attributes annotated `@JdbcTypeCode(SqlTypes.JSON)`) MUST be declared as `jsonb` on
POSTGRES, `JSON` on H2, and **`VARCHAR(MAX)` — never `NVARCHAR(MAX)`** on MS_SQL_SERVER. The SQL Server mapping is
pinned to the non-nationalized descriptor by `SqlServerJsonAsVarcharTypeContributor`; an `NVARCHAR(MAX)` JSON column
fails `ddl-auto: validate` at startup, and the only test that catches it is the SQL Server functional suite (part of
`./gradlew test`, excluded from `testFast`). See `specs/database-and-migrations/spec.md` § "JSON attribute storage
type is pinned per vendor" for the rationale and its known limitation.

## After editing

- The PostToolUse hook (`.claude/hooks/generate-db-schema.sh`) regenerates `docs/db-schema.md` automatically when migration files change.
- For non-Claude workflows, run `./gradlew generateDbSchema` and commit the updated `docs/db-schema.md`.
- `docs/db-schema.md` is auto-generated; do not edit it by hand.

## Java vs SQL migrations

Use SQL for schema DDL and simple data shifts. For migrations needing Java logic (e.g. JSON transforms), use the Java migration tree at `src/main/java/db/migration/` instead — see the CLAUDE.md there.
