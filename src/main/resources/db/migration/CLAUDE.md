# SQL migrations

Flyway-managed schema and data migrations, one directory per supported database vendor. Spec: `specs/database-and-migrations/spec.md`. Constitution: `.specify/memory/constitution.md` § Multi-Vendor Database Pattern.

## Naming

- `V{major}.{minor}__{Description}.sql` — dot separator (standard Flyway). Example: `V1.59__AddSomeColumn.sql`.

## Vendor parity

Three subdirectories: `H2/`, `POSTGRES/`, `MS_SQL_SERVER/`. New migrations MUST be added to **all three** unless the change is intentionally vendor-specific (in which case state why in the file or commit message). Mismatched versions across vendors will cause Flyway baseline drift.

## After editing

- The PostToolUse hook (`.claude/hooks/generate-db-schema.sh`) regenerates `docs/db-schema.md` automatically when migration files change.
- For non-Claude workflows, run `./gradlew generateDbSchema` and commit the updated `docs/db-schema.md`.
- `docs/db-schema.md` is auto-generated; do not edit it by hand.

## Java vs SQL migrations

Use SQL for schema DDL and simple data shifts. For migrations needing Java logic (e.g. JSON transforms), use the Java migration tree at `src/main/java/db/migration/` instead — see the CLAUDE.md there.
