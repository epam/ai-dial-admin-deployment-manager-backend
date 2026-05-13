# Research — Explicit Node Pool Scheduling

Phase 0 output. Resolves the implementation decisions left open by the spec and the plan.

## 1. YAML parsing of `NODE_POOLS` (FR-001, FR-003, FR-022)

**Decision**: Use Jackson with `YAMLFactory` from `jackson-dataformat-yaml`. Configure the `ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES = true` for strict mode. Register the Fabric8 model classes (`io.fabric8.kubernetes.api.model.Affinity`, `io.fabric8.kubernetes.api.model.Toleration`) directly as the field types on `NodePoolProperties.PoolConfig`, so Jackson handles the K8s sub-structure deserialization via the Fabric8-provided POJO bean shapes.

**Rationale**:
- `jackson-dataformat-yaml` is already on the classpath via Spring Boot's `spring-boot-starter` (it's the parser Spring uses for `application.yml`). No new dependency.
- Fabric8 model classes are Jackson-compatible POJOs with property setters; they have canonical K8s field names (camelCase) which match what operators write in `kubectl` manifests. Deserialization is essentially free.
- YAML is a syntactic superset of JSON, so single-line JSON inputs continue to parse — FR-001's "YAML or JSON-as-YAML" property falls out automatically.
- `FAIL_ON_UNKNOWN_PROPERTIES` is the natural strict-mode hook: it covers both the top-level deprecated fields (`maxNodes`, `cpu`, `memory`, `gpu`) and unknown K8s sub-fields uniformly with one config flag.

**Alternatives considered**:
- **SnakeYAML directly**: lower-level than Jackson, would require manual mapping to Fabric8 POJOs. Rejected — Jackson + YAMLFactory does both layers at once with less code.
- **Custom regex/string parsing**: rejected — reinvents the wheel and loses K8s schema fidelity.
- **Spring's `@ConfigurationProperties` binding directly to a YAML-shaped POJO via Spring's standard env-var path-binding (`APP_NODE_POOLS_0_NAME=...`)**: rejected — operator UX for the deeply-nested affinity / matchExpressions structure is hostile. Operators already write these in YAML elsewhere; meet them there.

## 2. Fabric8 model classes in `NodePoolProperties`

**Decision**: Declare pool primitive fields directly with Fabric8 types: `Map<String, String> nodeSelector`, `io.fabric8.kubernetes.api.model.Affinity affinity`, `List<io.fabric8.kubernetes.api.model.Toleration> tolerations`. No intermediate POJO mirror.

**Rationale**:
- Constitution Principle III scopes the Fabric8-only-in-`kubernetes/` rule to "non-configuration code". `configuration/` is the documented exception — `NodePoolProperties` already lives there.
- Mirroring the Fabric8 types into custom POJOs would either (a) lose schema fidelity (a custom `Affinity` mirror is a maintenance burden as K8s API evolves) or (b) be a verbatim copy that adds zero value.
- Manifest generators (`service/manifest/`) already consume Fabric8 types when they project primitives onto pod templates; passing them through unmodified avoids translation layers.

**Alternatives considered**:
- **Custom YAML-only POJOs that get translated to Fabric8 in the manifest layer**: rejected — duplication; also moves the translation logic outside the `configuration/` exception zone where it actually belongs.
- **A Map<String, Object> "free-form" structure passed verbatim to K8s API**: rejected — loses validation; FR-005 specifically requires schema validation against the K8s API model at startup, which is exactly what typed deserialization gives us for free.

## 3. Strict deserialization — covering both top-level and nested fields

**Decision**: Two-layer strict check. (a) `ObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)` catches unknown fields at every level. (b) An explicit pre-deserialization scan of the raw YAML tree (via `JsonNode`) checks specifically for the spec-named deprecated keys (`maxNodes`, `cpu`, `memory`, `gpu` on a pool entry; top-level `NODE_POOL_LABEL_KEY` env var) and produces a more helpful error than Jackson's generic "unrecognized property" message.

**Rationale**:
- Operators upgrading from Feature 016 will most often hit the deprecated-field case. A targeted message ("Field `maxNodes` is removed in this version; see docs/configuration.md for the new shape") saves a round-trip through documentation.
- The pre-scan is cheap (parse once into `JsonNode`, walk the tree) and runs only at startup, so the cost is negligible.
- Falls back to Jackson's generic unknown-field error for fields not in the deprecated list — covers typos and K8s schema misspellings uniformly.

**Alternatives considered**:
- **Rely on Jackson's `UnknownPropertyHandler` callback for custom messages**: rejected — adds indirection compared to a simple pre-scan, and the callback fires per-field during a streaming parse, making aggregate error reporting harder.

## 4. Listing API response shape — exposing pool primitives + defaults

**Decision**: Response shape is:

```jsonc
{
  "pools": [
    {
      "name": "gpu_pool",
      "description": "...",                   // optional, omitted if absent
      "nodeSelector": { "key": "value" },      // optional, omitted if absent
      "affinity": { /* full K8s Affinity */ }, // optional, omitted if absent
      "tolerations": [ /* K8s Toleration[] */ ]// optional, omitted if absent
    }
    /* … */
  ],
  "defaults": {
    "default": "cpu_pool",                     // optional, omitted if NODE_POOL_DEFAULT unset
    "model": "gpu_pool"                        // optional, omitted if NODE_POOL_DEFAULT_MODEL unset
  }
}
```

The current Feature 016 endpoint returns a bare JSON array; this is a breaking change to the response shape (acceptable per spec — same release ships matching FE changes). Field omission (not `null`, not `{}`) is enforced via Jackson `@JsonInclude(JsonInclude.Include.NON_EMPTY)`.

**Rationale**:
- The FE needs both the pool list and the defaults to pre-populate the picker (Story 4 scenario 12, FR-017). Returning them in a single payload halves the round-trip cost.
- A top-level `defaults` block is unambiguous about whether a default is set vs the pool with that name happening to be first in the list.
- `NON_EMPTY` (not `NON_NULL`) handles both `affinity == null` and `affinity == new Affinity()` (empty object) cases — Fabric8 model classes sometimes deserialize "missing" as empty objects depending on parent context.

**Alternatives considered**:
- **Keep the bare array, add a separate `GET /api/v1/node-pools/defaults` endpoint**: rejected — two round-trips for every form load; harder to keep consistent under concurrent admin restarts.
- **Encode defaults as a `defaultFor: [model, default]` array per pool entry**: rejected during spec clarification — the relationship is a top-level fact, not a property of the pool. Encoding it per-pool also complicates the "same pool is default for both" case.

## 5. Validation timing for defaults — when and where

**Decision**: `NodePoolConfiguration#nodePoolProperties(...)` bean factory method performs three sequential validations after parsing `NODE_POOLS`:

1. Per-pool schema validation (Hibernate Validator + Fabric8 model schema via `FAIL_ON_UNKNOWN_PROPERTIES`).
2. Cross-pool validation: unique names, non-blank names.
3. Defaults validation: each set value of `NODE_POOL_DEFAULT` / `NODE_POOL_DEFAULT_MODEL` must be a name in the loaded pool list.

Failure at any step throws `IllegalArgumentException` with a context-prefixed message; Spring's `BeanCreationException` wraps it, and the application fails to start. This is FR-015's "MUST refuse to start" behaviour.

**Rationale**:
- `@Bean` factory exceptions at startup produce the same fail-fast guarantee whether they originate from this validator or from Spring's own config binding. No new exception-handling code needed.
- Sequential ordering ensures that if pool parsing fails, defaults aren't checked against a half-built list — error messages stay coherent.
- The whole validation runs in <10ms for any realistic pool count; no need for async / lazy validation.

**Alternatives considered**:
- **`@PostConstruct` on `NodePoolValidationService`**: works but introduces a second component for what is conceptually one validation pass. Inlining inside the bean factory keeps related logic together.
- **Spring's `Validator` + `@Validated` on `@ConfigurationProperties`**: rejected — `@ConfigurationProperties` binding here is bespoke (we parse a YAML blob from a single env var), not Spring's hierarchical key binding. The standard `@Validated` machinery doesn't apply cleanly.

## 6. Create-time cascade — where in `DeploymentService`

**Decision**: Inject `NodePoolService` into `DeploymentService`. Inside the existing `create(...)` method, before persisting the `DeploymentEntity`, call `NodePoolService.resolveForCreate(deploymentType, requestPayload.nodePool(), payloadHadNodePoolFieldExplicitly)`. The resolution method returns the value to stamp (which may be null). It runs purely on the in-memory `NodePoolProperties` bean.

For `update(...)`: no cascade call. The update path validates a non-null `nodePool` against the pool set via `NodePoolService.isValid(name)`; explicit null is accepted verbatim.

For `duplicate(...)`: copy source's `nodePool` verbatim into the duplicate's "creation payload"; pass `payloadHadNodePoolFieldExplicitly = true` to the create path so the cascade is bypassed; existing validation rejects unresolvable names.

**Rationale**:
- Keeping the cascade behind a method on `NodePoolService` means there's one place to amend if the cascade ever changes (e.g. a third category in the future).
- The "explicit vs absent" distinction must flow from the controller (which observes the JSON wire form) down to the service. Spring's JSON deserialization to a Java record collapses absent and `null`, so we need a sentinel — see Decision 7.
- Duplicate reuses the same code path as create, just with `bypassCascade=true`. No new transactional boundary, no new state.

**Alternatives considered**:
- **Cascade in the controller**: rejected — violates Principle I (controllers don't do business logic).
- **Cascade in `NodePoolDtoMapper`**: rejected — mappers translate shapes, not decide values.
- **Cascade as a Spring AOP interceptor**: rejected — invisible at the call site, hard to test.

## 7. Distinguishing "field absent" from "field=null" on create

**Decision**: Change the controller's create request DTO from `String nodePool` to `JsonNullable<String> nodePool` (using `org.openapitools:jackson-databind-nullable`, which Spring's OpenAPI ecosystem already supports). `JsonNullable.isPresent()` → field was present in the JSON; `JsonNullable.get() == null` → present and null. Three distinguishable states: undefined, null, value.

**Rationale**:
- Standard idiom for this exact problem; well-supported by Jackson + SpringDoc.
- Keeps the create-vs-update semantic distinction crisp: create's "absent → cascade" is `!isPresent()`; create's "explicit null → store null" is `isPresent() && get() == null`; create's "explicit name" is `isPresent() && get() != null`.
- Update's PATCH-like semantics use the same machinery: absent → leave unchanged; null → set to null.

**Alternatives considered**:
- **A custom enum `NodePoolDirective { ABSENT, EXPLICIT_NULL, EXPLICIT_VALUE }`** plus a Jackson deserializer: rejected — `JsonNullable` already exists for exactly this and is widely understood.
- **Two distinct request DTOs (create-with-default vs create-without)** selected by a query parameter: rejected — UX hostile.
- **Treat absent and null identically (always cascade)**: rejected — removes the user's ability to say "I deliberately want Any" without breaking the cascade for routine programmatic creates.

## 8. Workload classification (FR-016)

**Decision**: A `WorkloadClassifier` static utility (or a small `@Component` if it needs anything injected — likely not) with a single method `boolean isModelWorkload(DeploymentType type)` returning `true` for `DeploymentType.NIM` and `DeploymentType.KSERVE_INFERENCE` (or whatever the existing enum names are) and `false` otherwise. Hard-coded; not env-configurable.

**Rationale**:
- FR-016 explicitly says system-defined and not configurable.
- A simple utility is easier to test than an injected component for a stateless function.
- New deployment types added later require a one-line code change in this utility — discoverable via grep on the enum name.

**Alternatives considered**:
- **A `@isModelWorkload` annotation on the deployment-type enum constants**: rejected — Java enum annotations work but add machinery for no real benefit at this scale.
- **A config-bound set of model-workload type names**: rejected per FR-016 (classification is code, not config).

## 9. Manifest generator signature change

**Decision**: Replace the existing `@Nullable Map<String, String> nodePoolLabels` parameter in `KnativeManifestGenerator`, `NimManifestGenerator`, `InferenceManifestGenerator` with a new value type `PoolSchedulingPrimitives` (Java record) carrying:

```java
public record PoolSchedulingPrimitives(
        @Nullable Map<String, String> nodeSelector,
        @Nullable Affinity affinity,
        @Nullable List<Toleration> tolerations
) {}
```

The caller (`DeploymentService` at deploy time) resolves the deployment's stored `nodePool` name to a pool entry and constructs this record from the entry's primitives (or passes a "null primitives" sentinel for null `nodePool`). Each generator applies the three sections to the appropriate slot on the pod template / CRD pod-template equivalent, per FR-006.

**Rationale**:
- Three fields in one carrier is cleaner than three nullable parameters.
- Records are immutable and `@Nullable` accommodates the "pool declares only some primitives" case.
- The "verbatim projection" requirement (FR-006) maps trivially to "if non-null, set the field; else leave alone."

**Alternatives considered**:
- **Pass the whole `PoolConfig` object** to each generator: rejected — wider surface than needed; generators don't care about `name` or `description`.
- **Three separate calls (`applyNodeSelector`, `applyAffinity`, `applyTolerations`) on the generator**: rejected — fragments the responsibility and makes "no pool → apply nothing" harder to express atomically.

## 10. Export / Import — strip nodePool

**Decision**: In `DeploymentExportService`, when serializing a `DeploymentEntity` to the export shape, the `nodePool` field is not included in the output DTO (either omit the field from the export DTO record entirely, or annotate with `@JsonIgnore` if the same DTO is shared with the internal API). On import, the import-DTO (which is deserialized from the imported file) does not declare a `nodePool` property; if a legacy export carries one, Jackson silently drops it (since `FAIL_ON_UNKNOWN_PROPERTIES` defaults to false on the import path — verify against current codebase). Imported deployments then flow through `DeploymentService.create(...)` with `nodePool` absent → cascade applies per FR-021.

**Rationale**:
- FR-021: pool is environment-specific; importing a name from a foreign environment would either fail validation or require cross-env reconciliation, both of which the spec rules out.
- Letting the cascade run on import gives the target environment's defaults a chance to populate, matching what would happen if the deployment were created fresh in the target.

**Alternatives considered**:
- **Keep `nodePool` in export and validate on import**: rejected during spec clarification (Q2 superseded by Q5).
- **Strip in export, reject on import if present**: rejected — extra friction without value; silently ignoring is friendlier and matches Jackson's default behaviour.

## 11. `docs/configuration.md` rewrite scope

**Decision**: Rewrite the entire "Node Pool Configuration" section in `docs/configuration.md`. New content covers:

1. Three configuration entries with property keys, env vars, defaults, and required-when conditions:
   - `app.node-pools.pools` / `NODE_POOLS`
   - `app.node-pools.default` / `NODE_POOL_DEFAULT`
   - `app.node-pools.default-model` / `NODE_POOL_DEFAULT_MODEL`
2. The YAML document shape with a per-field table (`name`, `description`, `nodeSelector`, `affinity`, `tolerations`) and a complete worked example matching the spec's user description.
3. The startup validation rules (strict parsing, defaults must reference existing pools).
4. The deploy-time-by-name resolution note required by the Documentation Deliverables section of the spec — explicitly calls out that editing a pool's primitives in `NODE_POOLS` propagates on next redeploy of every deployment using that pool, while changing a default env var never migrates existing deployments. This asymmetry is the key operator-facing point.
5. Removal note: `NODE_POOL_LABEL_KEY` is gone; presence causes startup failure. Operators upgrading from Feature 016 must rewrite their `NODE_POOLS` document into the new shape.

**Rationale**: The spec's Documentation Deliverables section is explicit about all of this. Pinning the scope here so the tasks generation phase produces a single, well-defined task rather than a vague "update docs" placeholder.
