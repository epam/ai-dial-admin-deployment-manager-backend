# Research: Node Pool Selector

## R1: Node Pool Configuration Shape

**Decision**: Node pools configured as a YAML list under `app.node-pools` in `application.yml`, bound via `@ConfigurationProperties` to a `NodePoolProperties` class containing a `List<NodePoolConfig>`.

**Rationale**: Follows existing patterns (`app.knative.deploy`, `app.nim.deploy`). Configuration-driven approach matches the spec requirement (FR-004). YAML list is natural for a small set of named pools. Per constitution, defaults live in `application.yml` only; Java fields have no initializers.

**Alternatives considered**:
- Database-stored pools: Rejected — spec says pools are operator-configured, not user-managed. Config file is the right level.
- Single flat properties per pool: Rejected — list structure is cleaner for N pools and supports the label selector map naturally.

## R2: Kubernetes Node Query Approach

**Decision**: Use Fabric8 `KubernetesClient.nodes().withLabelSelector(labels).list()` to find nodes per pool, then `KubernetesClient.pods().inAnyNamespace().withField("spec.nodeName", nodeName).list()` to get pod resource requests per node.

**Rationale**: Fabric8 7.5.2 supports label-based node listing natively. Pod resource requests (sum of `requests` across all containers) give "scheduled" resource usage — this matches the spec's "scheduled/requested resources" language. No metrics API needed.

**Alternatives considered**:
- Metrics API (actual usage): Rejected — spec explicitly asks for scheduled/requested resources, not real-time utilization. Metrics API also requires metrics-server and has availability concerns.
- Informers for node state: Rejected — spec says no caching; live query per request is simpler and per clarification Q4.

## R3: Resource Quantity Format

**Decision**: CPU in millicores (long), memory in bytes (long), GPU as integer count. All returned as numeric fields in the JSON response.

**Rationale**: Kubernetes natively reports CPU in millicores and memory in bytes. Returning raw numeric values lets the client format for display (e.g., "172 / 288 vCPU", "3.8 / 5.7 TB") without the server needing to know display preferences. GPU is always a whole number (nvidia.com/gpu extended resource).

**Alternatives considered**:
- Kubernetes Quantity strings (e.g., "64Gi", "128m"): Rejected — harder for clients to aggregate and compare.
- Floating-point cores: Rejected — millicores as long avoids floating-point precision issues.

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
