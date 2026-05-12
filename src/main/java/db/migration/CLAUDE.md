# Java migrations

Flyway Java callbacks for migrations that need Java logic — e.g. parsing or transforming JSON columns where pure SQL is awkward. Spec: `specs/database-and-migrations/spec.md`.

## Naming

- `V{major}_{minor}__{Description}.java` — **underscore** separator (Java class names cannot contain `.`). Both `V1_25__Foo.java` and a hypothetical `V1.25__Foo.sql` resolve to the same logical Flyway version.

## Subdirectories

| Path | Applies to |
|---|---|
| `H2/` | H2 only |
| `POSTGRES/` | PostgreSQL only |
| `MS_SQL_SERVER/` | SQL Server only |
| `common/` | All vendors — used when the same Java logic works against every supported DB |

## When to choose Java over SQL

Default to SQL (`src/main/resources/db/migration/`). Reach for Java only when the migration involves reading existing data, deserializing it, transforming, and writing it back — and SQL alone can't express the transformation cleanly.
