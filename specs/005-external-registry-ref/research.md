# Research: External Registry Reference for Sources

**Feature**: `005-external-registry-ref`
**Date**: 2026-03-14

## Decisions

### D1 — ExternalRegistryRef structure: polymorphic discriminated type

**Decision**: `ExternalRegistryRef` is a Jackson polymorphic interface using `@JsonTypeInfo(use = NAME, property = "$type")` — identical to all 25 other polymorphic types in the codebase. Three typed subtypes + `GenericRef` fallback.

| Discriminator value | Java type | Identifying field |
|---------------------|-----------|-------------------|
| `"mcp-registry"` | `McpRegistryRef` | `packageName: String` |
| `"github"` | `GitHubRef` | `repo: String` |
| `"generic"` | `GenericRef` | `url: String` |

**Rationale**: Consistent with existing patterns (`Source`, `ImageSource`, `EnvVar`, `ProbeHandler`, etc.). Named fields per registry type give clients clear semantics; `GenericRef` preserves extensibility without free-form string discriminators.

**Alternatives considered**: Flat `{registryType: String, itemId: String}` — rejected because it conflates structurally distinct identifiers into one opaque field and has no canonical discriminator values.

---

### D2 — Persistence: existing JSON column, no migration

**Decision**: `externalRegistryRef` is stored as a nested field inside the existing `source` JSON column on both `image_definition` and `deployment` tables. No Flyway migration is needed.

**Rationale**: `@JdbcTypeCode(SqlTypes.JSON)` columns store the full polymorphic object as JSON. Adding a new nullable field to a Jackson-serialised record is backward-compatible: existing rows simply deserialise `null` for the missing field.

**Alternatives considered**: Separate `external_registry_ref` column — rejected; would require 3-vendor migration scripts and the field is logically part of the source definition.

---

### D3 — Placement: field on source object (not top-level on ImageDefinition)

**Decision**: `externalRegistryRef` is a nullable field on `DockerImageSource`, `GitDockerfileImageSource`, and `ImageReferenceSource` — not on `ImageDefinition` top-level.

**Rationale**: The user explicitly specified "inside image/deployment source". All three targeted source types are mutable objects with existing optional fields (`@Nullable`-annotated fields in their DTOs), so the pattern is established.

---

### D4 — Validation: `@NotBlank` only; no format enforcement

**Decision**: Each subtype field (`packageName`, `repo`, `url`) is validated with `@NotBlank` — rejects null and whitespace-only strings. No regex or URL format enforcement.

**Rationale**: Confirmed by clarification Q3. External registry naming schemes evolve independently; format constraints would couple the backend to third-party URL structures.

---

### D5 — DTO split: both create and read DTOs need the field

**Decision**: For image definitions, `DockerImageSourceDto` and `GitDockerfileImageSourceDto` serve both read and write — both get `@Nullable @Valid ExternalRegistryRefDto externalRegistryRef`. For deployments, `CreateImageReferenceDeploymentSourceRequestDto` (write) and `ImageReferenceDeploymentSourceDto` (read) are separate — both get the nullable field.

---

### D6 — Export/import: automatic via JSON serialisation

**Decision**: No changes to `ConfigExporter` or `DeploymentImporter`. Since `externalRegistryRef` is part of the JSON source column, it is automatically included in exported payloads and restored on import through the existing serialisation chain.

**Rationale**: `ConfigExporter.addDeployment` serialises the full `Deployment` model to `ExportConfig`. The source object (including `externalRegistryRef`) is serialised as part of this. `DeploymentImporter.importOne` creates from `Deployment` — the same model carries the field through.

---

### D7 — FR-004 enforcement: type-system constraint, no explicit rejection code

**Decision**: The type system naturally prevents `externalRegistryRef` on registry-bound sources. `InferenceDeploymentHuggingFaceSourceDto` and `NimDeploymentNgcRegistrySourceDto` are in separate DTO hierarchies that simply do not have the field. Spring Boot's default `ignoreUnknown = true` means sending `externalRegistryRef` in an inference/NIM source request silently ignores the field — it does not cause an error. This is acceptable: the field is informational and the risk of silent discard on a wrong type is low.

---

### D8 — MapStruct mapper strategy

**Decision**: Two new mappers are created following the project convention:
- `ExternalRegistryRefDtoMapper` (web layer): `ExternalRegistryRefDto ↔ ExternalRegistryRef`, uses `@SubclassMapping` for the three subtypes.
- `PersistenceExternalRegistryRefMapper` (dao layer): `ExternalRegistryRef ↔ PersistenceExternalRegistryRef`, same pattern.

Existing mappers modified:
- `ImageSourceDtoMapper`: add `uses = {ExternalRegistryRefDtoMapper.class}` — MapStruct auto-maps the new field.
- `PersistenceImageSourceMapper`: add `uses = {PersistenceExternalRegistryRefMapper.class}`.
- `PersistenceDeploymentMapper`: add `uses = {PersistenceExternalRegistryRefMapper.class}` — MapStruct auto-maps the field in `PersistenceImageReferenceSource`.
- `DeploymentDtoMapper`: update the manual `switch` pattern for `ImageReferenceSource` to include the new `externalRegistryRef` component; pass it through `ExternalRegistryRefDtoMapper`.

---

### D9 — `ImageReferenceSource` record component addition

**Decision**: Add `@Nullable ExternalRegistryRef externalRegistryRef` as the second component. All construction sites that currently pass a single `String imageReference` must be updated to also pass `null` or the appropriate ref.

**Sites to update**:
- `DeploymentDtoMapper.applyCreateImageSource` — pass `null` (no ref in create request without the field) or the mapped ref from `CreateImageReferenceDeploymentSourceRequestDto`.
- Any test fixtures constructing `ImageReferenceSource`.

---

### D10 — No `@JsonInclude(NON_NULL)` change needed

**Decision**: The project does not configure global `NON_NULL` serialisation. The spec allows "absent or null" for records without a ref. Both behaviours are acceptable. No annotation change is required on source types.

---

## Unchanged areas

- No changes to `ImageDefinitionService`, `DeploymentService`, `ConfigExporter`, `DeploymentImporter`.
- No Kubernetes manifests or Knative resources affected.
- No Flyway migrations.
- `@LogExecution` already applied at class level on all existing Spring components; no new components added.
- No new `@ConfigurationProperties` or `application.yml` entries → `docs/configuration.md` does not need updating.
