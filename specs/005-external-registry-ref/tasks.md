# Tasks: External Registry Reference for Sources

**Input**: Design documents from `specs/005-external-registry-ref/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

**Organization**: Tasks are grouped by user story. US1 and US2 can be implemented in parallel once Phase 2 is complete.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies within phase)
- **[Story]**: Which user story this task belongs to (US1–US4)

---

## Phase 1: Setup

No new packages or project structure needed — all new files go into existing packages.

- [ ] T001 Read `specs/005-external-registry-ref/plan.md`, `data-model.md`, and `contracts/` to load the full implementation context before starting

---

## Phase 2: Foundational — ExternalRegistryRef Type Hierarchy

**Purpose**: The three new type hierarchies (model / persistence / DTO) and two new mappers are blocking prerequisites for every user story. Complete this phase before touching any source class.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T002 [P] Create `ExternalRegistryRef` interface and its three model subtypes in `src/main/java/com/epam/aidial/deployment/manager/model/`:
  - `ExternalRegistryRef.java` — interface with `@JsonTypeInfo(use = NAME, property = "$type")` and `@JsonSubTypes` for `McpRegistryRef` (`"mcp-registry"`), `GitHubRef` (`"github"`), `GenericRef` (`"generic"`)
  - `McpRegistryRef.java` — `public record McpRegistryRef(String packageName) implements ExternalRegistryRef {}`
  - `GitHubRef.java` — `public record GitHubRef(String repo) implements ExternalRegistryRef {}`
  - `GenericRef.java` — `public record GenericRef(String url) implements ExternalRegistryRef {}`

- [ ] T003 [P] Create `PersistenceExternalRegistryRef` interface and its three persistence subtypes in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/`:
  - `PersistenceExternalRegistryRef.java` — interface with same `@JsonTypeInfo`/`@JsonSubTypes` pattern as T002 (matching discriminator values)
  - `PersistenceMcpRegistryRef.java` — `public record PersistenceMcpRegistryRef(String packageName) implements PersistenceExternalRegistryRef {}`
  - `PersistenceGitHubRef.java` — `public record PersistenceGitHubRef(String repo) implements PersistenceExternalRegistryRef {}`
  - `PersistenceGenericRef.java` — `public record PersistenceGenericRef(String url) implements PersistenceExternalRegistryRef {}`

- [ ] T004 [P] Create `ExternalRegistryRefDto` interface and its three DTO subtypes in `src/main/java/com/epam/aidial/deployment/manager/web/dto/`:
  - `ExternalRegistryRefDto.java` — interface with same `@JsonTypeInfo`/`@JsonSubTypes` pattern (matching discriminator values)
  - `McpRegistryRefDto.java` — `public record McpRegistryRefDto(@NotBlank String packageName) implements ExternalRegistryRefDto {}`
  - `GitHubRefDto.java` — `public record GitHubRefDto(@NotBlank String repo) implements ExternalRegistryRefDto {}`
  - `GenericRefDto.java` — `public record GenericRefDto(@NotBlank String url) implements ExternalRegistryRefDto {}`

- [ ] T005 Create `ExternalRegistryRefDtoMapper` in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/ExternalRegistryRefDtoMapper.java`:
  - `@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)`
  - `@SubclassMapping` for all three DTO→model directions: `McpRegistryRefDto→McpRegistryRef`, `GitHubRefDto→GitHubRef`, `GenericRefDto→GenericRef`
  - `@SubclassMapping` for all three model→DTO directions
  - Bidirectional methods: `ExternalRegistryRef toModel(ExternalRegistryRefDto dto)` and `ExternalRegistryRefDto toDto(ExternalRegistryRef model)`
  - Depends on T002, T004

- [ ] T006 Create `PersistenceExternalRegistryRefMapper` in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceExternalRegistryRefMapper.java`:
  - `@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)`
  - `@SubclassMapping` for all three model→persistence directions and reverse
  - Bidirectional methods: `PersistenceExternalRegistryRef toEntity(ExternalRegistryRef model)` and `ExternalRegistryRef toDomain(PersistenceExternalRegistryRef entity)`
  - Depends on T002, T003

**Checkpoint**: Run `./gradlew checkstyleMain` — all new files must pass. Run `./gradlew testFast` — must compile and pass.

---

## Phase 3: User Story 1 — Attach ref to Image Definition Sources (Priority: P1) 🎯 MVP

**Goal**: Operators can set, update, and read `externalRegistryRef` on `DockerImageSource` and `GitDockerfileImageSource` in image definitions via the existing image definition CRUD endpoints.

**Independent Test**: Create an image definition with `$type: "git"` source including `externalRegistryRef: { $type: "mcp-registry", packageName: "my-server" }`. Read it back via `GET /api/v1/images/definitions/{id}`. Assert the source contains the typed reference. Then update with a `GitHubRef`. Then update without the field. Verify the three states in sequence.

- [ ] T007 [P] [US1] Modify `DockerImageSource` in `src/main/java/com/epam/aidial/deployment/manager/model/DockerImageSource.java`:
  - Add `@Nullable private ExternalRegistryRef externalRegistryRef;` field (Lombok `@Data` will generate getter/setter)
  - Depends on T002

- [ ] T008 [P] [US1] Modify `GitDockerfileImageSource` in `src/main/java/com/epam/aidial/deployment/manager/model/GitDockerfileImageSource.java`:
  - Add `@Nullable private ExternalRegistryRef externalRegistryRef;` field
  - Depends on T002

- [ ] T009 [P] [US1] Modify persistence records in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/`:
  - `PersistenceDockerImageSource.java` — add `PersistenceExternalRegistryRef externalRegistryRef` as the last record component (nullable — no annotation needed for Jackson)
  - `PersistenceGitDockerfileImageSource.java` — same addition
  - Depends on T003

- [ ] T010 [P] [US1] Modify DTO records in `src/main/java/com/epam/aidial/deployment/manager/web/dto/`:
  - `DockerImageSourceDto.java` — add `@Nullable @Valid ExternalRegistryRefDto externalRegistryRef` as the last record component
  - `GitDockerfileImageSourceDto.java` — same addition
  - Depends on T004

- [ ] T011 [US1] Update `ImageSourceDtoMapper` in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/ImageSourceDtoMapper.java`:
  - Add `uses = {ExternalRegistryRefDtoMapper.class}` to the `@Mapper` annotation
  - MapStruct will auto-map `externalRegistryRef` by field name — no explicit `@Mapping` needed
  - Verify `toImageSource(ImageSourceDto)` and `toImageSourceDto(ImageSource)` both compile and handle the new field
  - Depends on T005, T007, T008, T010

- [ ] T012 [US1] Update `PersistenceImageSourceMapper` in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceImageSourceMapper.java`:
  - Add `uses = {PersistenceExternalRegistryRefMapper.class}` to the `@Mapper` annotation
  - Verify bidirectional mappings compile with new field
  - Depends on T006, T007, T008, T009

- [ ] T013 [US1] Add functional test scenarios to `src/test/java/com/epam/aidial/deployment/manager/functional/h2/ImageDefinitionFunctionalTest.java` (and mirror in Postgres/SQL Server test classes if they exist):
  - `shouldCreateImageDefinitionWithMcpRegistryRef()` — POST git source with McpRegistryRef, GET back, assert ref present with correct packageName
  - `shouldCreateImageDefinitionWithGitHubRef()` — POST docker source with GitHubRef, GET back, assert ref present
  - `shouldCreateImageDefinitionWithGenericRef()` — POST with GenericRef, GET back, assert ref present
  - `shouldCreateImageDefinitionWithoutExternalRef()` — POST without ref, GET back, assert ref absent/null
  - `shouldUpdateImageDefinitionExternalRef()` — create with McpRegistryRef, PUT with GitHubRef, GET back, assert updated
  - `shouldClearImageDefinitionExternalRef()` — create with ref, PUT without ref, GET back, assert ref absent
  - `shouldFailCreateImageDefinition_whenExternalRefFieldBlank()` — POST with `{ "$type": "mcp-registry", "packageName": "" }`, assert HTTP 400
  - Depends on T011, T012

**Checkpoint**: `./gradlew testFast --tests "*.ImageDefinitionFunctionalTest"` must pass. User Story 1 independently validated.

---

## Phase 4: User Story 2 — Attach ref to Deployment ImageReferenceSource (Priority: P1)

**Goal**: Operators can set, update, and read `externalRegistryRef` on `ImageReferenceSource` in deployments (MCP/Adapter/Interceptor types only) via the existing deployment CRUD endpoints.

**Independent Test**: Create a deployment with `$type: "image_reference"` source including `externalRegistryRef: { $type: "mcp-registry", packageName: "my-server" }`. Read it back via `GET /api/v1/deployments/{id}`. Assert the source contains the typed reference.

- [ ] T014 [P] [US2] Modify `ImageReferenceSource` record in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/ImageReferenceSource.java`:
  - Change to: `public record ImageReferenceSource(String imageReference, @Nullable ExternalRegistryRef externalRegistryRef) implements Source {}`
  - Update all construction sites — primarily in `DeploymentDtoMapper` where `new ImageReferenceSource(imageReference)` is called; pass `null` as the second argument until T016 is done
  - Depends on T002

- [ ] T015 [P] [US2] Modify `PersistenceImageReferenceSource` record in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/PersistenceImageReferenceSource.java`:
  - Change to: `public record PersistenceImageReferenceSource(String imageReference, PersistenceExternalRegistryRef externalRegistryRef) implements PersistenceSource {}`
  - Depends on T003

- [ ] T016 [P] [US2] Modify deployment source DTOs in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/`:
  - `ImageReferenceDeploymentSourceDto.java` — add `@Nullable @Valid ExternalRegistryRefDto externalRegistryRef` as the last record component
  - `CreateImageReferenceDeploymentSourceRequestDto.java` — same addition
  - Depends on T004

- [ ] T017 [US2] Update `DeploymentDtoMapper` in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/DeploymentDtoMapper.java`:
  - Add `uses = {ExternalRegistryRefDtoMapper.class}` to the `@Mapper` annotation (or inject `ExternalRegistryRefDtoMapper` via `@Autowired`)
  - Update the `toDeploymentSourceDto` manual switch for `ImageReferenceSource`:
    ```java
    case ImageReferenceSource(String imageReference, ExternalRegistryRef externalRegistryRef) ->
        new ImageReferenceDeploymentSourceDto(imageReference,
            externalRegistryRefDtoMapper.toDto(externalRegistryRef));
    ```
  - Update `applyCreateImageSource` (or equivalent method) to pass `externalRegistryRef` from `CreateImageReferenceDeploymentSourceRequestDto` when constructing `ImageReferenceSource`
  - Depends on T005, T014, T016

- [ ] T018 [US2] Update `PersistenceDeploymentMapper` in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceDeploymentMapper.java`:
  - Add `uses = {PersistenceExternalRegistryRefMapper.class}` to the `@Mapper` annotation
  - Verify `@SubclassMapping` for `PersistenceImageReferenceSource ↔ ImageReferenceSource` still resolves — MapStruct will auto-map `externalRegistryRef` via the added mapper
  - Depends on T006, T014, T015

- [ ] T019 [US2] Add functional test scenarios to `src/test/java/com/epam/aidial/deployment/manager/functional/h2/DeploymentFunctionalTest.java` (and mirror in Postgres/SQL Server test classes):
  - `shouldCreateDeploymentWithMcpRegistryRef()` — POST `image_reference` source with McpRegistryRef, GET back, assert ref present
  - `shouldCreateDeploymentWithGenericRef()` — POST with GenericRef, GET back, assert ref present
  - `shouldCreateDeploymentWithoutExternalRef()` — POST without ref, GET back, assert ref absent/null
  - `shouldUpdateDeploymentExternalRef()` — create with McpRegistryRef, PUT with GitHubRef, GET back, assert updated
  - `shouldClearDeploymentExternalRef()` — create with ref, PUT without ref, GET back, assert ref absent
  - `shouldFailCreateDeployment_whenExternalRefFieldBlank()` — POST with blank `url` on GenericRef, assert HTTP 400
  - `shouldListDeployments_withMixedExternalRefs()` — create two deployments (one with ref, one without), GET list, assert correct per-record presence
  - Depends on T017, T018

**Checkpoint**: `./gradlew testFast --tests "*.DeploymentFunctionalTest"` must pass. User Story 2 independently validated.

---

## Phase 5: User Story 3+4 — Read Lists & Remove (Priority: P1/P2)

**Goal**: US3 (read from list endpoints with correct ref present/absent) and US4 (clear/remove ref) are validated by the functional tests in T013 and T019. This phase adds the list-endpoint read scenario for image definitions and verifies export/import round-trip behavior.

**Independent Test**: Seed multiple image definitions and deployments with varying refs. Call the list endpoints. Assert each record has the correct ref. Export config including a record with a ref. Import it. Assert the ref survived the round-trip.

- [ ] T020 [US3] Add list-read test scenarios to `ImageDefinitionFunctionalTest`:
  - `shouldListImageDefinitions_withMixedExternalRefs()` — create three image definitions (McpRegistryRef, GenericRef, no ref), GET `api/v1/images/definitions`, assert each record shows correct/absent ref
  - Depends on T013

- [ ] T021 [US3] Add export/import round-trip test to verify FR-012 (externalRegistryRef preserved through export/import):
  - Add `shouldPreserveExternalRefThroughExportImport()` to the appropriate config export/import functional test class
  - Create image definition or deployment with a ref, export config, import to a clean state, assert ref is present after import
  - Depends on T013, T019

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T022 Run `./gradlew checkstyleMain checkstyleTest` and fix any style violations across all new and modified files

- [ ] T023 Run `./gradlew testFast` (H2 full suite) — all tests must pass

- [ ] T024 Run `./gradlew test` (full suite including Postgres and SQL Server via Testcontainers) — all tests must pass across all three vendors

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (T001) → Phase 2 (T002–T006) → Phase 3 (T007–T013) ─┐
                                      → Phase 4 (T014–T019) ─┤→ Phase 5 → Phase 6
```

- **Phase 1**: No dependencies
- **Phase 2**: Depends on Phase 1. **BLOCKS** Phase 3 and Phase 4
- **Phase 3 and Phase 4**: Both depend only on Phase 2 — can run in parallel
- **Phase 5**: Depends on Phase 3 and Phase 4
- **Phase 6**: Depends on Phase 5

### Within Phase 2 (new type hierarchy)

```
T002 [P]─┐
T003 [P]─┤→ T005 (dao mapper: T002+T003)
T004 [P]─┤→ T006 (web mapper: T002+T004)  (note: T005/T006 labels are in plan as reversed — verify)
         └→ T005
```

T002, T003, T004 fully parallel. T005 (ExternalRegistryRefDtoMapper) needs T002+T004. T006 (PersistenceExternalRegistryRefMapper) needs T002+T003.

### Within Phase 3 (image definition sources)

```
T007 [P]─┐
T008 [P]─┤─→ T011 (ImageSourceDtoMapper: T005+T007+T008+T010)
T009 [P]─┤─→ T012 (PersistenceImageSourceMapper: T006+T007+T008+T009)
T010 [P]─┘
              T011+T012 → T013 (functional tests)
```

T007–T010 fully parallel. T011 depends on T005+T007+T008+T010. T012 depends on T006+T007+T008+T009. T013 depends on T011+T012.

### Within Phase 4 (deployment sources)

```
T014 [P]─┐
T015 [P]─┤─→ T017 (DeploymentDtoMapper: T005+T014+T016)
T016 [P]─┤─→ T018 (PersistenceDeploymentMapper: T006+T014+T015)
         └
              T017+T018 → T019 (functional tests)
```

T014–T016 fully parallel. T017 depends on T005+T014+T016. T018 depends on T006+T014+T015. T019 depends on T017+T018.

---

## Parallel Example: Phase 2

```text
# Launch all three type hierarchies simultaneously:
Task T002: Create ExternalRegistryRef model hierarchy (3 files)
Task T003: Create PersistenceExternalRegistryRef hierarchy (3 files)
Task T004: Create ExternalRegistryRefDto hierarchy (3 files)

# Then:
Task T005: ExternalRegistryRefDtoMapper (after T002+T004)
Task T006: PersistenceExternalRegistryRefMapper (after T002+T003)
```

## Parallel Example: Phase 3 + Phase 4 (once Phase 2 is complete)

```text
# Launch Phase 3 and Phase 4 concurrently:

Agent A — Phase 3 (image definition sources):
  T007, T008, T009, T010 in parallel → T011 → T012 → T013

Agent B — Phase 4 (deployment sources):
  T014, T015, T016 in parallel → T017 → T018 → T019
```

---

## Implementation Strategy

### MVP First (US1 only — image definition sources)

1. Complete Phase 2: Foundational type hierarchy (T002–T006)
2. Complete Phase 3: Image definition source changes (T007–T013)
3. **STOP and VALIDATE**: Run `./gradlew testFast --tests "*.ImageDefinitionFunctionalTest"` — all tests pass
4. US1 is fully functional and shippable independently

### Incremental Delivery

1. Phase 2 complete → foundation ready
2. Phase 3 complete → US1 functional (image definition sources with registry refs)
3. Phase 4 complete → US2 functional (deployment sources with registry refs)
4. Phase 5 complete → US3+US4 validated (list reads + clear path)
5. Phase 6 → production-ready (full test suite across all DB vendors)

---

## Notes

- **No Flyway migration needed** — `externalRegistryRef` lives in the existing JSON `source` column. Existing rows deserialise with `null` for the missing field.
- **No service layer changes** — `ImageDefinitionService` and `DeploymentService` pass the source object through unchanged; the new field is transparent to business logic.
- **Export/import automatic** — `ConfigExporter` and `DeploymentImporter` serialise the full source object; the new field is preserved without code changes.
- **Checkstyle before committing** — run `./gradlew checkstyleMain` after each phase; fix violations immediately.
- **Record construction sites** — when `ImageReferenceSource` record gains a second component, the compiler will flag every `new ImageReferenceSource(imageReference)` call. Update each to `new ImageReferenceSource(imageReference, null)` temporarily in T014, then pass the real ref value in T017.
