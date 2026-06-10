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

## Applied migrations are immutable

Once a migration has shipped, its **behaviour** must never change — fresh databases must end up byte-identical to databases migrated by older releases. Concretely:

- Java migrations stay on **Jackson 2** (`com.fasterxml.jackson.*`) via `db.migration.MigrationJsonMapper` — do NOT migrate them to `tools.jackson` or point them at the application's `JsonMapperConfiguration`, whose semantics evolve with the app.
- Flyway stores a `null` checksum for Java (`JDBC`-type) migrations — only `.sql` files get content checksums — so `validate` will NOT catch behavioural drift in Java migrations. Discipline is the only guard.
- Editing an applied `.sql` migration DOES change its checksum and crashes every existing installation at startup validation. Never edit applied SQL files; add a new version instead.
