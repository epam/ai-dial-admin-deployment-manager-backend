# Implementation Plan: Automatic Pull Secrets for Trusted-Registry Images

**Branch**: `025-auto-pull-secrets` | **Date**: 2026-06-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/025-auto-pull-secrets/spec.md`

## Summary

At deploy time the system already knows the administrator-configured registry credentials (primary registry + `TRUSTED_PRIVATE_REGISTRIES`), but those credentials are only consumed by the build/copy pipeline — never by the generated workload manifests. Consequently a deployment whose container image lives in a credentialed private registry cannot pull until an administrator hand-creates a `dockerconfigjson` secret and patches a service account.

This feature closes that gap. For each in-scope image (the deployment image of Knative image-based deployments, and the transformer container image of chained inference deployments), the deploy flow resolves whether a credentialed configured registry matches the image's registry host. When it does, the system provisions a per-deployment `kubernetes.io/dockerconfigjson` secret in the deployment's namespace and injects an `imagePullSecrets` reference into the generated CRD. Provisioning mirrors the existing sensitive-env-var secret pattern (`AbstractDeploymentManager.provisionSecrets` → `K8sClient.createSecret` → `DisposableResourceManager`), so it inherits deterministic naming, namespace resolution, idempotency, concurrency safety (per-deployment secret name), credential-rotation-on-redeploy, and automatic cleanup on undeploy. When no credentialed registry matches, nothing is injected and behavior is byte-for-byte unchanged. NIM is untouched (it keeps its existing NGC pull-secret mechanism).

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.0.6 (Spring Framework 7); Fabric8 Kubernetes Client 7.5.2 + Knative Client 7.5.2; KServe CRD model (`io.kserve.serving.v1beta1`); Jib `ImageReference` for image parsing; MapStruct 1.6.3; Lombok
**Storage**: No new schema. Reuses the existing `DisposableResource` table (`K8sResourceKind.SECRET`). Multi-vendor (H2/PostgreSQL/SQL Server) unaffected — no Flyway migration, no `generateDbSchema` run required.
**Testing**: JUnit 5 + AssertJ; manifest-generation unit tests; functional deploy tests on H2 (`*FunctionalTest`); no mocking of K8s calls in functional tests (constitution).
**Target Platform**: Linux container on Kubernetes (Knative Serving + KServe).
**Project Type**: Single-module backend web service (Gradle).
**Performance Goals**: Negligible deploy-time overhead — at most one additional `Secret` create per deploy of an in-scope, matched deployment; zero overhead when unmatched or feature-disabled.
**Constraints**: Purely additive — must not alter manifests for public/unmatched images (FR-006), must not change the build pipeline (FR-013), must not conflict with NIM's NGC secret (FR-012). Provisioning must be idempotent (FR-008), rotation-correct (FR-009), and concurrency-safe (FR-010). All K8s mutations confined to the `kubernetes/` package (constitution III).
**Scale/Scope**: Per-deployment, all namespaces the manager already deploys into.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitutional rule | Compliance in this design |
|---|---|
| **I. Strict layered architecture** | Credential resolution + provisioning orchestration live in `service/` (new `RegistryPullSecretProvisioner` service + a resolver on `RegistryService`); the `Secret` is built by `service/manifest/ManifestGenerator` (object only, no API call); the actual `createSecret` is the existing `kubernetes/K8sClient`. No `web` → `dao`/`kubernetes` shortcut; no new controller. ✅ |
| **III. Kubernetes isolation** | The only K8s mutation is `K8sClient.createSecret(...)` (already in `kubernetes/`), invoked from the deployment manager exactly as `provisionSecrets()` does today. Manifest generators only assemble CRD objects. No new polling. ✅ |
| **IV. Observability first** | New Spring components (`@Service`/`@Component`) carry `@LogExecution`; resolution/skip decisions logged at debug with context. ✅ |
| **V. Security by configuration** | No new secret is logged; credentials flow only into the K8s `Secret` `stringData`. No hard-coded credentials. ✅ |
| **Configuration property defaults** | The one new flag's default is declared only in `application.yml` via `${AUTO_PULL_SECRET_ENABLED:true}`; the Java field has no initializer. ✅ |
| **Configuration documentation** | `docs/configuration.md` updated with the new env var (a planned task). ✅ |
| **Code style** | `StringUtils`/`CollectionUtils` for emptiness checks; `var`; no wildcard imports; 180-col. ✅ |
| **No per-feature checklists** | No `specs/025-auto-pull-secrets/checklists/` directory is created. ✅ |
| **Anti-patterns** | No business logic in entities (no new entity); no generic `catch (Exception)`; no `ddl-auto` change; no hard-coded secrets. ✅ |

**Result**: PASS. No violations → Complexity Tracking left empty.

## Project Structure

### Documentation (this feature)

```text
specs/025-auto-pull-secrets/
├── plan.md              # This file
├── research.md          # Phase 0 output — design decisions & rationale
├── data-model.md        # Phase 1 output — conceptual model (no DB change)
├── quickstart.md        # Phase 1 output — config + manual verification
├── contracts/           # Phase 1 output — CRD imagePullSecrets contract + internal provisioner contract
│   └── pull-secret-contract.md
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── configuration/
│   └── RegistryProperties.java                 # (existing) registry + trusted-registry config
├── docker/
│   ├── DockerRegistryClient.java               # (existing) image→registry matching to reuse
│   └── DockerHubAliases.java                    # (existing) sameRegistry() normalization to reuse
├── service/
│   ├── RegistryService.java                     # EDIT: add credential-resolution-by-image-host + dockerconfigjson builder
│   ├── RegistryPullSecretProvisioner.java       # NEW: orchestrates resolve→build→create→register for a deployment's in-scope images
│   ├── manifest/
│   │   ├── ManifestGenerator.java               # EDIT: add pullSecretConfig(name, dockerConfigJson) → type kubernetes.io/dockerconfigjson
│   │   ├── KnativeManifestGenerator.java         # EDIT: inject imagePullSecrets into RevisionSpec when provided
│   │   ├── InferenceManifestGenerator.java       # EDIT: thread transformer pull-secret name through
│   │   └── TextClassificationTransformerSection.java  # EDIT: set imagePullSecrets on Transformer when provided
│   └── deployment/
│       ├── AbstractDeploymentManager.java        # EDIT (or subclass hook): provision pull secret before manifest build
│       ├── KnativeDeploymentManager.java         # EDIT: resolve deployment image → provision → pass secret name to serviceConfig
│       └── InferenceDeploymentManager.java        # EDIT: resolve transformer image → provision → pass secret name
├── kubernetes/
│   └── K8sClient.java                            # (existing) createSecret(...) reused as-is
├── cleanup/resource/
│   └── DisposableResourceManager.java            # (existing) saveK8sResources/lifecycle reused as-is
└── utils/mapping/
    ├── KnativeMappers.java                        # EDIT: add RevisionSpec.imagePullSecrets field mapper
    └── InferenceMappers.java                      # EDIT: add Transformer.imagePullSecrets field mapper

src/main/resources/
└── application.yml                                # EDIT: add app.registry.auto-pull-secret-enabled flag

docs/
└── configuration.md                              # EDIT: document AUTO_PULL_SECRET_ENABLED

src/test/java/...                                  # NEW/EDIT: manifest unit tests + Knative & inference functional deploy tests
```

**Structure Decision**: Single-module backend. The feature slots into existing layers without new packages: a small new service (`RegistryPullSecretProvisioner`) plus targeted edits to `RegistryService`, the three manifest generators, the two affected deployment managers, the two mapper classes, and config/docs. The `kubernetes/` and `cleanup/` layers are reused unchanged.

## Complexity Tracking

> No constitutional violations — section intentionally empty.
