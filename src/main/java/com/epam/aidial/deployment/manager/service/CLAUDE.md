# Service layer

Business logic, transaction boundaries, pipelines, caching, scheduled jobs. See `.specify/memory/constitution.md` for the full architecture; this file lists only what's specific to this layer.

## Layer rules

- May call `dao/` and `kubernetes/`. MUST NOT be called *from* `dao/` or `kubernetes/`.
- `@Transactional` lives here (and on repository wrappers in `dao/`); not on controllers.
- `@LogExecution` MUST be on every `@Service` / `@Component`.
- Fabric8 / `io.kubernetes` types are forbidden in this layer — go through `kubernetes/` package abstractions (constitution Principle III).
- `catch (Exception e)` is forbidden here; catch specific types. Top-level `Exception` catch is allowed only in `web/handler/DefaultExceptionHandler`.

## Patterns

- Service-level mappers (not DTO, not persistence) live under `mapper/` at the project root, named `*Mapper`. Don't put them in `web/mapper/` or `dao/mapper/`.
- Cached lookups: Caffeine + `@Cacheable` / `@CacheEvict`.
- Scheduled jobs that must run only once across replicas: ShedLock `@SchedulerLock`.
- Image build pipeline: chain of steps under `service/pipeline/step/`. Add a new step by extending the chain rather than mutating existing steps.

## Subpackages

| Path | Responsibility |
|---|---|
| `audit/` | Audit activity recording, history queries (Hibernate Envers integration) |
| `config/` | Configuration import/export orchestration (ZIP archive build/parse, conflict resolution) |
| `deployment/` | Deployment lifecycle services — base, mcp, interceptor, adapter, application, inference, nim |
| `manifest/` | KNative / KServe / NIM manifest generation; env var injection; probe converters |
| `nodepool/` | Node-pool selector resolution for K8s scheduling |
| `pipeline/` | Image build pipeline — multi-step chain in `pipeline/step/`, specifications in `pipeline/specification/` |
| `security/` | Security mode resolution, JWT provider selection (azure / keycloak / auth0 / okta / cognito) |

Top-level files in `service/` are utilities and single-purpose services that don't fit a subpackage cluster (e.g. `SseEmitterFactory`, `JobSpecification`, `McpClientFactory`).

## Related specs

- `specs/<capability>/spec.md` for the capability you're touching (e.g. `deployments`, `image-builds`, `mcp-servers`, `inference-deployments`).
