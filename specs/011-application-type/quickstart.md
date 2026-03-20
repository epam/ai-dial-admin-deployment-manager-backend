# Quickstart: Application Image Definition & Deployment Type

## Overview

This feature adds "APPLICATION" as a new image definition and deployment type, identical to Adapter. All new classes are empty marker subclasses — no new business logic. The work is primarily registration: adding the new type to all polymorphic hierarchies, mappers, switches, and enums.

## Implementation Order

1. **Database migrations** — Create `application_image_definition` and `application_deployment` tables (V1.54)
2. **Model layer** — New model classes + update parent `@JsonSubTypes` + update `ImageType` enum
3. **Entity layer** — New JPA entity classes
4. **DTO layer** — New DTO classes + update parent `@JsonSubTypes` + update `DeploymentTypeDto` enum
5. **Mapper layer** — Add `@SubclassMapping` to all 4 MapStruct mappers + DeploymentMapper
6. **Repository layer** — Add switch cases in `detectEntityClass` and `toEntityClass`
7. **Service layer** — Update `KnativeDeploymentManager`, `DeploymentManagerProvider`, `ConfigExporter`, importers, `ExportConfig`
8. **Tests** — Add Application test helpers + functional test methods
9. **Schema docs** — Run `./gradlew generateDbSchema`

## Key Pattern

Every new file mirrors its Adapter counterpart. For example:

```java
// AdapterImageDefinition.java (existing)
@SuperBuilder
@AllArgsConstructor
public class AdapterImageDefinition extends ImageDefinition { }

// ApplicationImageDefinition.java (new — identical structure)
@SuperBuilder
@AllArgsConstructor
public class ApplicationImageDefinition extends ImageDefinition { }
```

## Verification

```bash
./gradlew checkstyleMain checkstyleTest    # Code style
./gradlew testFast                          # H2 tests (fast)
./gradlew test                              # Full suite with testcontainers
./gradlew generateDbSchema                  # Regenerate schema docs
```

## Files Changed Summary

- **9 new files**: 3 model, 4 DTO, 2 entity classes
- **3 new migrations**: H2, Postgres, MS SQL Server
- **~18 modified files**: parent classes (JsonSubTypes), enums, mappers, repositories, services, config, tests
