# Research: NIM Service URL Schema Prefix

## R1: Current URL Resolution Behavior

**Decision**: The `resolveServiceUrl` method in `NimDeploymentManager` (lines 180–205) reads either `model.getClusterEndpoint()` or `model.getExternalEndpoint()` from the NIMService CRD status and returns the raw value with no schema prefix manipulation.

**Rationale**: The NIM Kubernetes operator populates these endpoint fields without schema prefixes (e.g., `my-service.namespace.svc.cluster.local:8000`). Downstream consumers (DIAL Core) expect fully-qualified URLs with `http://` or `https://`.

**Alternatives considered**: None — this is an observation of current state, not a design choice.

## R2: Schema Override Configuration Pattern

**Decision**: Add a single `urlSchema` property to `NimDeployProperties` (prefix `app.nim.deploy`), exposed as env var `K8S_NIM_DEPLOYMENT_URL_SCHEMA`. When set, it overrides the default schema for all NIM service URL resolutions.

**Rationale**: Follows the existing configuration pattern in `NimDeployProperties` (flat fields with env var bindings in `application.yml`). A single override property keeps the configuration surface minimal. Per-deployment overrides are not needed at this time.

**Alternatives considered**:
- Separate `clusterUrlSchema` and `externalUrlSchema` properties — rejected as over-engineering for current use cases.
- System-wide (non-NIM-specific) schema property — rejected as other deployment managers (Knative, KServe) have independent URL resolution logic.

## R3: Schema Detection for Already-Prefixed URLs

**Decision**: Before prepending a schema, check if the URL already starts with `http://` or `https://` (case-insensitive). If it does, return the URL as-is.

**Rationale**: Defensive measure against future changes in the NIM operator that might start returning prefixed URLs. Prevents malformed double-prefixed URLs like `http://http://host:port`.

**Alternatives considered**:
- Regex-based URI parsing — rejected as overkill; simple `startsWith` check is sufficient and more readable.

## R4: Override Value Normalization

**Decision**: Strip trailing `://` from the override value if present, then always append `://` when constructing the final URL. This means both `https` and `https://` as override values produce `https://`.

**Rationale**: Prevents operator error when configuring the override — accepting either format is more user-friendly.

**Alternatives considered**:
- Strict validation requiring exact format — rejected as unnecessarily strict for a simple string prefix.
