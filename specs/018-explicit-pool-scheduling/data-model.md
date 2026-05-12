# Data Model — Explicit Node Pool Scheduling

Phase 1 output. Concrete Java type layout for the in-memory configuration, the persisted entity, the wire DTOs, and the service-layer carrier types.

## 1. Configuration entities (`configuration/`)

`NodePoolProperties` is loaded once at startup from `application.yml` / env vars by `NodePoolConfiguration` and bound as a Spring `@Bean`. It contains the parsed pool catalogue plus the two default pool names. Fields are declared without Java initializers (Constitution PATCH 1.2.2); defaults live exclusively in `application.yml`.

```java
@Getter
@Setter
@NoArgsConstructor
public class NodePoolProperties {

    /** Pool catalogue from NODE_POOLS / app.node-pools.pools. Empty list when not configured. */
    @Valid
    private List<PoolConfig> pools;

    /** Id of the catch-all default pool (NODE_POOL_DEFAULT). Null when not configured. */
    @Nullable
    private String defaultPoolId;

    /** Id of the model-workload override default pool (NODE_POOL_DEFAULT_MODEL). Null when not configured. */
    @Nullable
    private String defaultModelPoolId;

    public Optional<PoolConfig> findById(String id) {
        if (CollectionUtils.isEmpty(pools)) {
            return Optional.empty();
        }
        return pools.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public boolean isPoolConfigured() {
        return CollectionUtils.isNotEmpty(pools);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PoolConfig {

        /** Immutable machine identifier — the foreign-key-style reference from deployments. Required, unique. */
        @NotBlank(message = "'id' is required and must not be blank")
        private String id;

        /** Human-readable display label — safe to rename. Required, recommended unique across pools. */
        @NotBlank(message = "'name' is required and must not be blank")
        private String name;

        @Nullable
        private String description;

        /** Optional simple key=value node selector. */
        @Nullable
        private Map<String, String> nodeSelector;

        /** Optional full Kubernetes Affinity object. Fabric8 model class is Jackson-friendly. */
        @Nullable
        private Affinity affinity;

        /** Optional list of standard Kubernetes Tolerations. */
        @Nullable
        private List<Toleration> tolerations;
    }
}
```

**Removed from Feature 016**: `nodePoolLabelKey`, `getLabelSelector(...)`, `minNodes`, `maxNodes`, `instance`, `gpu`, `cpu`, `memory`, `GpuSpec`, `CpuSpec`, `MemorySpec`. Their absence is enforced by FR-003's strict YAML parsing.

**Identity & uniqueness**: `id` (immutable, foreign-key-style identifier referenced by deployments) MUST be unique across `pools`. `name` (display-only label) MUST also be unique across `pools` (enforced for now; relaxation is a future-feature decision). Both are enforced by `NodePoolConfiguration` after parsing, before bean publication. Renaming `name` between restarts is non-breaking; changing `id` is breaking — it orphans deployments that referenced the old value.

**Default-references-existing-pool invariant**: `defaultPoolId` and `defaultModelPoolId`, when non-null, MUST match a `pools[].id` value. Enforced at startup (FR-015).

## 2. Persisted entity (`dao/entity/deployment/DeploymentEntity`)

The Feature 016 column `node_pool` is **renamed** to `node_pool_id` to reflect the id-vs-name split (see Clarifications Session 2026-05-12). Migration `V1.59__RenameDeploymentNodePoolToNodePoolId.sql` performs a pure column rename on both `deployment` and the audit mirror `deployment_aud` — no data transformation. To preserve continuity for existing rows across the cutover, operators are advised in `docs/configuration.md` to set each pool's `id` on the first post-migration config to the value the pool previously had under `name`.

```java
@Column(name = "node_pool_id")
private String nodePoolId;
```

Length VARCHAR(255) is ample for pool ids.

**Permitted states**:

| Stored value | Meaning |
|---|---|
| Non-null pool id in current `NODE_POOLS` | Pool's primitives apply at deploy time; read responses populate `nodePoolName` from the pool's current `name`. |
| Non-null pool id **not** in current `NODE_POOLS` (dangling) | Read-side returns the id verbatim with `nodePoolName: null`; redeploy fails (Feature 016 semantics, retained). |
| `null` | "Any" — no pool primitives at deploy time, `nodePoolName: null` on the response. Reachable from explicit user selection, from the cascade terminating at null on create, or from a legacy record. |

## 3. Service-layer carrier (`service/manifest/`)

New record used to pass pool primitives from `DeploymentService` to the manifest generators in a single value:

```java
public record PoolSchedulingPrimitives(
        @Nullable Map<String, String> nodeSelector,
        @Nullable Affinity affinity,
        @Nullable List<Toleration> tolerations
) {

    public static final PoolSchedulingPrimitives EMPTY = new PoolSchedulingPrimitives(null, null, null);

    public boolean isEmpty() {
        return MapUtils.isEmpty(nodeSelector) && affinity == null && CollectionUtils.isEmpty(tolerations);
    }

    public static PoolSchedulingPrimitives of(NodePoolProperties.PoolConfig pool) {
        if (pool == null) {
            return EMPTY;
        }
        return new PoolSchedulingPrimitives(pool.getNodeSelector(), pool.getAffinity(), pool.getTolerations());
    }
}
```

Placement: `service/manifest/PoolSchedulingPrimitives.java` (alongside the generators that consume it).

## 4. Wire DTOs (`web/dto/nodepool/`)

### Listing response

`NodePoolListResponseDto` wraps the pool list and the defaults block (FR-017):

```java
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NodePoolListResponseDto(
        @Schema(description = "Configured pool catalogue")
        List<NodePoolDto> pools,
        @Schema(description = "Currently-configured fallback defaults")
        DefaultsDto defaults
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record DefaultsDto(
            @Nullable
            @Schema(description = "Pool id of the catch-all default pool (NODE_POOL_DEFAULT)")
            String defaultId,
            @Nullable
            @Schema(description = "Pool id of the model-workload override default pool (NODE_POOL_DEFAULT_MODEL)")
            String modelId
    ) {}
}
```

### Per-pool DTO

`NodePoolDto` is reshaped — add the immutable `id` alongside the display `name`, plus the three primitive sections. Fabric8 types are serialized as-is by Jackson (canonical K8s field names):

```java
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NodePoolDto(
        @Schema(description = "Immutable pool id", example = "gpu-a100")
        String id,
        @Schema(description = "Human-readable display label", example = "GPU A100 Pool")
        String name,
        @Nullable
        @Schema(description = "Human-readable description")
        String description,
        @Nullable
        @Schema(description = "Optional node selector (key=value map)")
        Map<String, String> nodeSelector,
        @Nullable
        @Schema(description = "Optional Kubernetes Affinity object", implementation = Affinity.class)
        Affinity affinity,
        @Nullable
        @Schema(description = "Optional list of Kubernetes Tolerations")
        List<Toleration> tolerations
) {}
```

**Removed from Feature 016**: `instance`, `minNodes`, `maxNodes`, `gpu`, `cpu`, `memory`, plus the `GpuSpecDto` / `CpuSpecDto` / `MemorySpecDto` records.

### Deployment create/update DTO field

The deployment create / update DTOs carry a nullable `String nodePoolId` (the persisted pool id). The field uses a `transient nodePoolIdFieldPresent` flag to distinguish "absent" from "explicit null" (FR-013, FR-018) without a `JsonNullable` dependency. The response DTO additionally carries a read-only `String nodePoolName` resolved from the current `NODE_POOLS` configuration at read time.

```java
@Data
public abstract class CreateDeploymentRequestDto {
    // ... existing fields ...
    @Nullable
    private String nodePoolId;             // input + persisted id

    @JsonIgnore
    private transient boolean nodePoolIdFieldPresent;

    public void setNodePoolId(String nodePoolId) {
        this.nodePoolId = nodePoolId;
        this.nodePoolIdFieldPresent = true;
    }
}

@Data
public abstract class DeploymentDto {
    // ... existing fields ...
    @Nullable
    private String nodePoolId;             // persisted id, or null
    @Nullable
    private String nodePoolName;           // resolved display label at read time, null when id is null or dangling
}
```

Three input states (Research §7):

| Wire form | Interpretation on create | Interpretation on update |
|---|---|---|
| field absent | Cascade resolves (FR-018) | Stored value unchanged |
| `"nodePoolId": null` | Stored as null ("Any") | Stored as null ("Any") |
| `"nodePoolId": "gpu-pool"` | Validated by id, stored | Validated by id, stored |

## 5. Export / import shape

Export DTO **excludes** `nodePool`. The export record does not carry the field; Jackson serializes only declared properties. (If the existing export-DTO is reused across internal API surfaces, use `@JsonIgnore` on the `nodePool` accessor in the export context only.)

Import DTO does not declare `nodePool`. Jackson's default behaviour silently ignores any incoming `nodePool` field on a legacy export — no configuration change needed (verify current `ObjectMapper` config has `FAIL_ON_UNKNOWN_PROPERTIES = false` on the import path; this is the typical Spring default).

## 6. Workload classification

```java
public final class WorkloadClassifier {

    private WorkloadClassifier() {}

    private static final Set<DeploymentType> MODEL_TYPES =
            EnumSet.of(DeploymentType.NIM, DeploymentType.KSERVE_INFERENCE);

    public static boolean isModelWorkload(DeploymentType type) {
        return MODEL_TYPES.contains(type);
    }
}
```

Placement: `service/nodepool/WorkloadClassifier.java`. Adjust the enum constants to match the actual deployment-type enum in the codebase (verify exact names during implementation; if `KSERVE_INFERENCE` is named differently, e.g. `INFERENCE`, substitute accordingly).

## 7. Validation cross-references

| FR | Enforced by | Type-level mechanism |
|---|---|---|
| FR-001 / FR-022 | `NodePoolConfiguration` | YAML parsing of `NODE_POOLS` env var / `application.yml` key |
| FR-002 | `PoolConfig` (Jakarta `@NotBlank`, `@Valid`); `NodePoolConfiguration` cross-pool uniqueness scan |
| FR-003 | `ObjectMapper` configured with `FAIL_ON_UNKNOWN_PROPERTIES`; pre-scan for legacy fields (Research §3) |
| FR-004 | `NodePoolConfiguration` startup check on `NODE_POOL_LABEL_KEY` env var presence |
| FR-005 | Jackson + Fabric8 typed deserialization fails on unknown nested fields / type mismatches |
| FR-006 / FR-007 | `KnativeManifestGenerator` / `NimManifestGenerator` / `InferenceManifestGenerator` consuming `PoolSchedulingPrimitives` |
| FR-008 | `DeploymentService` deploy path reads stored `nodePool`, looks up `PoolConfig`, projects primitives via generators |
| FR-009 / FR-010 | Deploy-time logic is the same code path as initial deploy — primitives change naturally based on the stored value |
| FR-011 | `NodePoolDtoMapper` produces DTOs with `@JsonInclude(NON_EMPTY)` |
| FR-012 | Feature 020's utilisation block layered on `NodePoolDto` |
| FR-013 / FR-018 | `NodePoolService.resolveForCreate(...)` + `DeploymentService.create(...)` |
| FR-014 | `DeploymentService.update(...)` — no cascade, explicit values validated, omission ignored |
| FR-015 | `NodePoolConfiguration` startup defaults-validation pass (Research §5) |
| FR-016 | `WorkloadClassifier.isModelWorkload(...)` |
| FR-017 | `NodePoolListResponseDto.DefaultsDto` |
| FR-019 | DeploymentEntity persistence is the canonical store; no system code mutates it on env-var change |
| FR-020 | `DeploymentDuplicationService` constructs an internal create request with `bypassCascade=true` and the source's `nodePool` |
| FR-021 | `DeploymentExportService` omits `nodePool`; import-DTO does not declare it |
