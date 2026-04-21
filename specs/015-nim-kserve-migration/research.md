# Research: NIM KServe Migration & Configurable Storage Size

**Status**: Complete (post-factum documentation)

## R1: Storage size field type — String vs Long

**Decision**: String (Kubernetes quantity format)

**Rationale**: The NIM CRD `Pvc.size` field is a String. Operators use Kubernetes quantity notation (`20Gi`, `500Mi`). Using a Long (bytes) would require conversion at manifest generation and force unintuitive API usage (e.g., `21474836480` instead of `"20Gi"`).

**Alternatives considered**:
- Long (bytes): Rejected — lossy conversion, poor DX, NIM CRD description says "Size of the NIM cache in Gi"
- Integer (Gi only): Rejected — too restrictive, doesn't support Mi/Ti or plain bytes

## R2: Validation approach — Regex vs Library

**Decision**: Fabric8 `Quantity` parser (via `KubernetesQuantityParser` utility)

**Rationale**: Fabric8 is already a project dependency. Its `Quantity.parse()` and `Quantity.getAmountInBytes()` handle all Kubernetes quantity formats correctly, including binary suffixes (Ki, Mi, Gi, Ti, Pi, Ei), decimal suffixes (k, M, G, T), plain bytes, and fractional values.

**Alternatives considered**:
- Custom regex: Initially used `^[1-9][0-9]*(Ki|Mi|Gi|Ti|Pi|Ei)?$` — rejected because it missed decimal suffixes and fractional values
- Jakarta `@Pattern` annotation: Rejected — same regex limitations, no byte-level comparison for upper bound

## R3: Architecture — Web layer accessing Fabric8 Quantity

**Decision**: Extract `KubernetesQuantityParser` utility class in `utils/` package

**Rationale**: The project's ArchUnit rule (`webLayerMustNotAccessKubernetes`) prevents web layer classes from importing `io.fabric8.kubernetes.*`. The parser utility lives in `utils/` (not constrained), and the web validator calls it indirectly.

**Alternatives considered**:
- Move validator to service layer: Rejected — validation belongs in the web/DTO layer per project conventions
- Exempt validator from ArchUnit rule: Rejected — weakens the architecture rule

## R4: Max storage size configuration location

**Decision**: `app.validation.resources.max-storage-size` (env `RESOURCES_STORAGE_MAX_SIZE`, default `200Gi`)

**Rationale**: Grouped under existing `validation.resources` namespace alongside `max-cpu-in-cores`, `max-memory-in-mb`, `max-nvidia-gpu`. Consistent naming and location.

**Alternatives considered**:
- `app.validation.storage.max-size`: Initially used — moved to `resources` namespace for consistency
- No default (nullable): Initially used — changed to `200Gi` default to prevent accidental over-provisioning
