# Research: Add Version to McpRegistryRef

**Feature**: `016-mcp-registry-version`
**Date**: 2026-04-10

## Research Summary

No NEEDS CLARIFICATION items were identified during planning. The feature is fully scoped and all technical decisions are clear. Research below documents the decisions for traceability.

## Decision: Storage mechanism for version field

**Decision**: Add `version` as a new component to the existing `McpRegistryRef`, `McpRegistryRefDto`, and `PersistenceMcpRegistryRef` Java records. No database migration needed.

**Rationale**: `ExternalRegistryRef` is serialized as JSON inside the `source` column on both `image_definition` and `deployment` tables. Jackson naturally handles the new field:
- New rows with `version` → serialized with the field included

**Alternatives considered**:
- Flyway migration to add a dedicated column → Rejected. The ref is already nested JSON; adding a column would break the existing polymorphic JSON pattern.

## Decision: Validation approach for version field

**Decision**: Use `@NotBlank` annotation on the `version` field in `McpRegistryRefDto`. The field is required.

**Rationale**: The field is required and must be non-blank. `@NotBlank` is the standard Jakarta Bean Validation annotation for this — it rejects null, empty strings, and whitespace-only strings.

**Alternatives considered**:
- `@Nullable @Pattern` → Rejected. The version field is required, not optional.
- No validation (accept empty strings) → Rejected. Spec requires non-blank.

## Decision: MapStruct mapper changes

**Decision**: No mapper changes needed. MapStruct auto-maps fields with matching names between `McpRegistryRefDto`, `McpRegistryRef`, and `PersistenceMcpRegistryRef`. Since all three are records with the same field names, the existing `ExternalRegistryRefDtoMapper` and `PersistenceExternalRegistryRefMapper` will automatically pick up the new `version` field.

**Rationale**: MapStruct generates mapping code at compile time based on field names. Adding a field with the same name to both source and target records requires no explicit mapping configuration.

**Alternatives considered**: None — this is the standard MapStruct behavior.
