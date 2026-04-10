# Research: Add Version to McpRegistryRef

**Feature**: `016-mcp-registry-version`
**Date**: 2026-04-10

## Research Summary

No NEEDS CLARIFICATION items were identified during planning. The feature is fully scoped and all technical decisions are clear. Research below documents the decisions for traceability.

## Decision: Storage mechanism for version field

**Decision**: Add `version` as a new component to the existing `McpRegistryRef`, `McpRegistryRefDto`, and `PersistenceMcpRegistryRef` Java records. No database migration needed.

**Rationale**: `ExternalRegistryRef` is serialized as JSON inside the `source` column on both `image_definition` and `deployment` tables. Jackson naturally handles the new field:
- Existing rows without `version` in their JSON → deserialized as `null` (backward compatible)
- New rows with `version` → serialized with the field included

**Alternatives considered**:
- Flyway migration to add a dedicated column → Rejected. The ref is already nested JSON; adding a column would break the existing polymorphic JSON pattern.

## Decision: Validation approach for version field

**Decision**: Use `@NullOrNotBlank` custom validator (project already has precedents for nullable-but-not-blank validation) or conditional validation. If no `@NullOrNotBlank` exists, use `@jakarta.annotation.Nullable` without `@NotBlank`, and handle empty-string rejection via a custom validation approach consistent with how the project validates similar optional fields.

**Rationale**: The field is optional (null is valid) but when provided must be non-blank. Standard `@NotBlank` rejects null, which is not desired.

**Alternatives considered**:
- `@NotBlank` → Rejected. Would make the field required, breaking backward compatibility.
- No validation (accept empty strings) → Rejected. Spec requires non-blank when provided.

## Decision: MapStruct mapper changes

**Decision**: No mapper changes needed. MapStruct auto-maps fields with matching names between `McpRegistryRefDto`, `McpRegistryRef`, and `PersistenceMcpRegistryRef`. Since all three are records with the same field names, the existing `ExternalRegistryRefDtoMapper` and `PersistenceExternalRegistryRefMapper` will automatically pick up the new `version` field.

**Rationale**: MapStruct generates mapping code at compile time based on field names. Adding a field with the same name to both source and target records requires no explicit mapping configuration.

**Alternatives considered**: None — this is the standard MapStruct behavior.
