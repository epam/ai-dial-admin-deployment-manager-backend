# Configuration package

Spring `@Configuration` classes and `@ConfigurationProperties` value objects — datasource wiring, security, telemetry, HTTP clients, Kubernetes config, executors, retries. See `.specify/memory/constitution.md` for the full architecture; this file lists only what's specific to this package.

## Critical rule: defaults live only in `application.yml`

`@ConfigurationProperties` fields MUST be declared **without** Java initializers. Defaults are expressed once in `application.yml` via `${ENV_VAR:default}` syntax. See constitution § "Configuration property defaults".

```java
// CORRECT
private int maxRetries;

// WRONG — duplicates the YAML default; risks divergence
private int maxRetries = 3;
```

When you add a new env-var-backed property here:
- Declare the field with no initializer.
- Add the matching key to `application.yml` with `${ENV_VAR:default}`.
- Update `docs/configuration.md` (manually maintained — see constitution § "Configuration documentation").

## Subpackages

| Path | Responsibility |
|---|---|
| `datasource/` | Multi-vendor `DataSource` wiring; selects H2 / Postgres / SQL Server via `DATASOURCE_VENDOR` |
| `encryption/` | Symmetric encryption setup for sensitive env-var storage |
| `export/` | Configuration import/export wiring (used by `service/config/`) |
| `kubernetes/` | Fabric8 client configuration, informer wiring |
| `logging/` | `@LogExecution` AOP wiring (`CustomizableTraceInterceptor`) |

Top-level files are individual `@Configuration` / `*Properties` classes — one concern each.

## Related specs

- `specs/api-conventions/spec.md`, `specs/security/spec.md`, `specs/observability-and-logging/spec.md` for cross-cutting concerns wired here.
