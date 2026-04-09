# Research: NIM Served Model Name Override

## Decision 1: Injection Mechanism

**Decision**: Auto-inject `NIM_SERVED_MODEL_NAME` environment variable at manifest generation time, mirroring the inference `--model_name` pattern.

**Rationale**: The inference deployment already solves the same problem (model name override) using manifest-layer injection in `InferenceManifestGenerator.setModelNameIfNotSet()`. NIM uses an environment variable (`NIM_SERVED_MODEL_NAME`) instead of a CLI argument, but the "set if not set" logic is identical. This approach requires no new API fields, DTOs, database columns, or migrations — keeping the change minimal and consistent with established patterns.

**Alternatives considered**:
- **Dedicated `servedModelName` field across all layers**: New DTO field, entity field, DB migration, mapper changes. Rejected because it violates the existing precedent set by inference, adds unnecessary complexity for a single env var, and increases the API surface for something that already works via the generic env var mechanism.
- **No auto-injection (user must always set env var manually)**: Rejected because it's inconsistent with inference behavior where the system provides a sensible default, and places unnecessary burden on administrators.

## Decision 2: Default Value

**Decision**: Use the deployment identifier (same `name` parameter passed to `serviceConfig()`) as the default value for `NIM_SERVED_MODEL_NAME`.

**Rationale**: Matches the inference pattern where `--model_name` defaults to the deployment name. Provides a predictable, meaningful model identity that aligns with how the platform already identifies deployments.

**Alternatives considered**:
- **No default (leave unset)**: Rejected because NIM derives the name from the image, which is often opaque (e.g., `nvcr.io/nim/meta/llama-3.1-8b-instruct:1.8.2` → `meta/llama-3.1-8b-instruct`). The deployment ID is a better default for platform consistency.
- **Extract model name from image reference**: Rejected because image naming conventions vary and parsing is fragile.

## Decision 3: Check Scope for Existing Override

**Decision**: Check both `simpleEnvs` and `sensitiveEnvs` lists for an existing `NIM_SERVED_MODEL_NAME` entry before injecting the default.

**Rationale**: Users may provide the env var through either mechanism. Sensitive env vars use Kubernetes secrets (valueFrom.secretKeyRef), which is a valid way to pass a model name if the user considers it sensitive or wants it managed via secret rotation. The inference pattern only checks `command` and `args` because it uses CLI arguments, but for NIM we must check both env var types.

**Alternatives considered**:
- **Check only simple envs**: Rejected because a user could provide the override via a sensitive env var, and we'd silently overwrite it.
