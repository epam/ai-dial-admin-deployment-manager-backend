# Research: Node Pool Selector

## R1: Node Pool Configuration Shape

**Decision**: Node pools configured via a single `NODE_POOLS` environment variable containing a JSON array, parsed at startup by `NodePoolConfiguration` (following the `RegistryConfiguration` pattern). A cluster-wide `NODE_POOL_LABEL_KEY` env var defines the K8s label key; each pool's `name` serves as the label value. Each pool has structured specs: `gpu` (nullable, with name/vramBytes/count), `cpu` (with name nullable/milliCpus), `memory` (with bytes), plus optional `instance` and `description`.

**Rationale**: Single env var is easy to set at deploy time (Helm values, K8s ConfigMap). JSON parsing via `ObjectMapper` follows the established `RegistryConfiguration`/`GitConfiguration` pattern. Cluster-wide label key avoids redundant per-pool selector config — in practice, K8s clusters use one label key for node pools. Structured GPU/CPU/memory objects carry hardware details (model names, VRAM) needed by the UI. Startup validation ensures fail-fast on bad config.

**Alternatives considered**:
- Complex nested YAML with `@ConfigurationProperties`: Rejected — hard to modify after deployment without changing YAML files.
- Database-stored pools: Rejected — spec says pools are operator-configured, not user-managed.
- Per-pool label selector map: Rejected — over-general; a single label key with per-pool name as value is sufficient and simpler.

## R2: Node Pool API — Configuration Only, No K8s Queries

**Decision**: The node pools API returns only configuration data (from `NODE_POOLS` env var). No Kubernetes API calls are made. Live node utilization (running nodes, allocated resources) is deferred to a future iteration.

**Rationale**: Querying K8s for per-node utilization requires either N+1 API calls (1 node list + N pod lists per node) or a bulk pod list across all namespaces — both scale poorly. The admin selects a pool based on hardware specs (GPU model, VRAM, CPU, memory), not real-time load. Configuration-only response has zero latency and zero K8s API cost.

**Alternatives considered**:
- Live K8s node + pod queries per request: Rejected — O(nodes) API calls, expensive for large clusters.
- Bulk pod list + client-side grouping: Rejected — listing all pods in a cluster is heavy.
- Metrics API for actual usage: Rejected — requires metrics-server, doesn't cover GPU, adds complexity.

## R3: Resource Representation

**Decision**: Structured objects for each resource type: `gpu` (name, vramBytes, count — nullable for CPU-only pools), `cpu` (name nullable, milliCpus), `memory` (bytes). Units: CPU in millicores, memory/VRAM in bytes.

**Rationale**: Structured objects carry hardware details (GPU model, VRAM, processor name) needed by the UI to render rich pool cards. Bytes and millicores are machine-parseable and consistent with K8s conventions. Client formats for display.

## R4: Node Affinity Injection Strategy

**Decision**: Each manifest generator (`KnativeManifestGenerator`, `NimManifestGenerator`, `InferenceManifestGenerator`) receives the node pool's label selector (Map<String, String>) and applies `requiredDuringSchedulingIgnoredDuringExecution` node affinity with `matchExpressions` using `In` operator for each label key-value pair. When no pool is selected, no affinity is set (null/omitted).

**Rationale**: Hard affinity matches clarification Q1 (pods must not spill to other nodes). Using `matchExpressions` with `In` operator is the standard K8s pattern for label-based node selection. Each generator already has access to the pod template or CRD spec where affinity can be set.

**Injection points per deployment type**:
- **Knative**: `RevisionSpec` via `revisionSpecChain` in `KnativeManifestGenerator.serviceConfig()` — RevisionSpec exposes `setAffinity()`
- **NIM**: `NIMServiceSpec` via `specChain` in `NimManifestGenerator.serviceConfig()` — need to verify NIMService CRD supports affinity at spec level or via pod template
- **KServe**: `Predictor` via `predictorChain` in `InferenceManifestGenerator.serviceConfig()` — KServe Predictor supports node affinity via `nodeAffinity` field

**Alternatives considered**:
- `nodeSelector` (simple key-value map): Rejected — less flexible than affinity rules and harder to extend later if soft preferences are needed.
- Preferred affinity: Rejected per clarification Q1.

## R5: Deployment Field Approach

**Decision**: Add a single `nodePool` String field (nullable) to `DeploymentEntity`, `Deployment` domain model, `CreateDeployment`, `DeploymentDto`, and `CreateDeploymentRequestDto`. The field stores the node pool name. Validation resolves the name to config at create/update time and at deploy time.

**Rationale**: Storing the pool name (not the full config) keeps the schema simple and decouples the deployment record from config changes. A pool's label selector or spec can change in config without requiring a migration. Validation at both write time and deploy time (FR-007, FR-017) catches stale references.

**Alternatives considered**:
- Storing full pool config as JSON: Rejected — config duplication, harder to keep in sync.
- Storing label selector directly: Rejected — loses the pool abstraction; can't resolve pool name for display.
- Foreign key to a pools table: Rejected — pools are config-driven, not DB entities.

## R6: Node Pool Validation Strategy

**Decision**: Two validation points:
1. **Create/Update time** (FR-007): `DeploymentService` validates `nodePool` name against `NodePoolProperties` before persisting.
2. **Deploy time** (FR-017): `AbstractDeploymentManager` (or deployment service pre-deploy) validates `nodePool` still exists in config and resolves label selector for affinity injection.

**Rationale**: Double validation catches both immediate typos and config drift. The deploy-time check prevents deploying with a stale pool reference after config changes.

## R7: K8sClient Extension

**Decision**: Add two new methods to `K8sClient`:
1. `listNodes(Map<String, String> labelSelector)` → `List<Node>` — lists nodes matching labels
2. `listPodsOnNode(String nodeName)` → `PodList` — lists all pods scheduled on a specific node (across all namespaces)

**Rationale**: Follows existing K8sClient patterns (getPods, getJobPods). Separate methods keep responsibilities clear. Pod listing per node is needed to sum resource requests.

**Alternatives considered**:
- Single method returning aggregated data: Rejected — service layer should handle aggregation, not K8s client.
