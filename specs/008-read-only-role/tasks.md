# Tasks: Read-Only Admin Role

**Input**: Design documents from `/specs/008-read-only-role/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), data-model.md, contracts/

**Tests**: Included — the spec references the existing security test infrastructure and the reference PR includes tests for every controller.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the core types and configuration that all user stories depend on

- [x] T001 [P] Create `UserRole` enum with FULL_ADMIN and READ_ONLY_ADMIN values in `src/main/java/.../web/security/UserRole.java`
- [x] T002 [P] Create `FullAdminOnly` meta-annotation wrapping `@PreAuthorize("hasAuthority('FULL_ADMIN')")` in `src/main/java/.../web/security/FullAdminOnly.java`
- [x] T003 [P] Create `SecurityInfoDto` (with `userInfo` field) in `src/main/java/.../web/dto/SecurityInfoDto.java`
- [x] T004 [P] Create `UserInfoDto` (with `id`, `email`, `roles` fields) in `src/main/java/.../web/dto/UserInfoDto.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Role mapping resolution and authentication converter changes — MUST be complete before ANY user story can be implemented

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T005 Add `rolesMapping` field (JSON `String`) to `IdentityProvidersProperties.ProviderConfig` with Jackson validation in `checkProviders()`. Add `ObjectMapper` dependency and `providersRolesMapping` map for parsed results.
- [x] T006 [P] Add `rolesMapping` field to `JwtProviderConfig` and update `from()` factory method to take 3 params (name, config, rolesMapping). Mapping is passed from `IdentityProvidersProperties.providersRolesMapping`.
- [x] T007 [P] Add `rolesMapping` field to `OpaqueTokenProviderConfig` and update `from()` factory method to take 3 params (name, config, rolesMapping).
- [x] T008 Add `defaultRolesMapping` property via `IdentityProviderUtils`: inject `RolesMappingResolver` + `ObjectMapper`, parse `config.rest.security.default.roles-mapping` JSON string via Jackson, expose `getRolesMapping(providerAllowedRoles, providerRolesMapping)` method.
- [x] T009 Add `roles-mapping: "{}"` default property under `config.rest.security.default` in `src/main/resources/application.yml`
- [x] T010 Create `RolesMappingResolver` Spring `@Component` implementing the 4-level precedence cascade (provider roles-mapping → provider allowed-roles + default allowedRoles → default roles-mapping → default allowedRoles → empty) in `src/main/java/.../web/security/RolesMappingResolver.java`. Has `@LogExecution` and `@ConditionalOnProperty`.
- [x] T011 Create `UserRolesResolver` plain Java class that translates `Collection<? extends GrantedAuthority>` to application role authorities using a `Map<String, Set<UserRole>>` in `src/main/java/.../web/security/UserRolesResolver.java`
- [x] T012 Modify `JwtAuthenticationConverterFactory` — constructor takes `(providers, identityProviderUtils)` (no `RolesMappingResolver`). Uses `identityProviderUtils.getRolesMapping()` to resolve per-provider role mapping and creates `UserRolesResolver` per provider.
- [x] T013 Modify `JwtAuthenticationConverter` — accept `UserRolesResolver`, early return on empty roles (details not set on unauthenticated token).
- [x] T014 Modify `OpaqueAuthenticationConverterFactory` — same pattern as T012, uses `identityProviderUtils.getRolesMapping()`.
- [x] T015 Modify `OpaqueAuthenticationConverter` — accept `UserRolesResolver` (field order: userRolesResolver, emailClaims, requireEmail).
- [x] T016 Rename `SecurityConfiguration` → `OidcSecurityConfiguration`. Inject `PublicPathsResolver` instead of `disableSwaggerAuthorization`. Add `NimbusJwtDecoderResolver` bean. Update `IssuerToDecoderMapFactory` to use it. Remove `RolesMappingResolver` field (encapsulated in `IdentityProviderUtils`).
- [x] T016a Rename `NoSecurityConfiguration` → `NoneSecurityConfiguration`. Remove `@EnableMethodSecurity`.
- [x] T016b Create `BasicSecurityConfiguration` for `basic` auth mode with `PublicPathsResolver`.
- [x] T016c Create `PublicPathsResolver` — extracted public path patterns logic into shared `@Component`.
- [x] T016d Create `NimbusJwtDecoderResolver` interface. Update `IssuerToDecoderMapFactory` to use it (1-param `createIssuerToDecoderMap`). Update `TokenDecoderFactoryImpl` accordingly.
- [x] T017 Verify `DefaultExceptionHandler.handleAuthorizationException` uses `@ResponseStatus(HttpStatus.FORBIDDEN)` (already correct in our codebase).
- [x] T018 Add `getRoles()` method to `SecurityClaimsExtractor` that extracts `GrantedAuthority` names from the current `Authentication` object.

### Foundational Tests

- [x] T019 [P] Create `RolesMappingResolverTest` with tests for all precedence levels and edge cases.
- [x] T020 [P] Create `UserRolesResolverTest` with tests for role translation (matching, no matching, multiple, unknown, empty, mixed).
- [x] T021 Update `IdentityProviderTestHelper` — simplified (no rolesMapping on ProviderConfig since it's now a JSON String parsed during checkProviders).
- [x] T022 Delete `TestIdentityProviderConfig`, `TestAuthenticationConverterFactory`, `TestTokenDecoderFactory`. Replace with `TestSecurityConfig` (overrides `NimbusJwtDecoderResolver` with secret key) and `@TestPropertySource` provider config in `AbstractControllerSecurityTest`.
- [x] T023 Update `AbstractControllerSecurityTest` — provider config via `@TestPropertySource`, add `invalidJwt()`, `notAllowedRoles()`, `fullAdminRoles()`, `readOnlyAdminRoles()` helpers (with `TEST_ISSUER_2` for read-only), add `performPost`, `performPut`, `performDelete` helpers.
- [x] T024 Update `JwtAuthenticationConverterFactoryTest` — use `ObjectMapper` for `IdentityProviderUtils` constructor, pass `Map.of()` as rolesMapping to `JwtProviderConfig.from()`.
- [x] T025 Update `IdentityProvidersPropertiesTest` — add `ObjectMapper` dependency, add tests for invalid JSON, unknown roles, and valid roles-mapping.
- [x] T026 Run `./gradlew testFast` — all foundational changes compile and pass.

**Checkpoint**: Foundation ready — role mapping, auth converters, and test infrastructure updated.

---

## Phase 3: User Story 1+2+3 — Read-Only Viewing, Mutation Blocking, Full Admin Backward Compat (Priority: P1)

**Goal**: READ_ONLY_ADMIN users can view all entities (US1), are blocked from all mutating operations with 403 (US2), and FULL_ADMIN users retain full access (US3).

### Implementation

- [x] T027 [P] [US2] Add `@FullAdminOnly` to mutating methods on `DeploymentController`: `createDeployment`, `duplicateDeployment`, `changeImage`, `updateDeployment`, `deleteDeployment`, `deploy`, `undeploy`
- [x] T028 [P] [US2] Add `@FullAdminOnly` to `importConfig` on `ConfigController`. Do NOT annotate `previewImport`, `previewConfig`, or `exportConfig` (read-only POSTs)
- [x] T029 [P] [US2] Add `@FullAdminOnly` to `createImageDefinition`, `updateImageDefinition`, `deleteImageDefinition` on `ImageDefinitionController`
- [x] T030 [P] [US2] Add `@FullAdminOnly` to `buildImage` on `ImageBuildController`
- [x] T031 [P] [US2] Add `@FullAdminOnly` to `clean` on `DisposableResourceController`
- [x] T032 [P] [US2] Add `@FullAdminOnly` to `updateDomainWhitelistForImageBuild` on `GlobalDomainWhitelistController`

### Tests for US1+US2+US3

- [x] T033 [P] [US2] Expand `ImageDefinitionControllerSecurityTest` — test GET (all/by-id) accessible for both roles; DELETE returns 403 for READ_ONLY_ADMIN and 204 for FULL_ADMIN.
- [ ] T034 [P] [US2] Create `DeploymentControllerSecurityTest` — test mutating endpoints return 403 for READ_ONLY_ADMIN; GET endpoints return 200 for both roles.
- [ ] T035 [P] [US2] Create `ConfigControllerSecurityTest` — test `importConfig` returns 403 for READ_ONLY_ADMIN; read-only POSTs accessible for both.
- [ ] T036 [P] [US2] Create `ImageBuildControllerSecurityTest` — test `buildImage` returns 403 for READ_ONLY_ADMIN.
- [ ] T037 [P] [US2] Create `DisposableResourceControllerSecurityTest` — test `clean` returns 403 for READ_ONLY_ADMIN.
- [ ] T038 [P] [US2] Create `GlobalDomainWhitelistControllerSecurityTest` — test `updateDomainWhitelistForImageBuild` returns 403 for READ_ONLY_ADMIN; GET accessible for both.
- [ ] T039 Run `./gradlew testFast` — all annotations and security tests pass.

**Checkpoint**: US1+US2+US3 complete.

---

## Phase 4: User Story 5 — Security Info Endpoint (Priority: P2)

**Goal**: `/api/v1/security-info` returns the authenticated user's mapped application roles.

- [x] T040 [US5] Create `SecurityInfoController` at `/api/v1/security-info`. Inject `SecurityClaimsExtractor`, build `SecurityInfoDto` from extracted principal, email, and roles.
- [x] T041 [US5] Create `SecurityInfoControllerTest` (none mode) — test returns user info when mocked, test returns empty when no auth context.
- [ ] T042 [US5] Create `SecurityInfoControllerSecurityTest` (oidc mode) — test FULL_ADMIN returns roles `["FULL_ADMIN"]`, READ_ONLY_ADMIN returns roles `["READ_ONLY_ADMIN"]`, unauthenticated returns 401.
- [ ] T043 Run `./gradlew testFast` — security info endpoint tests pass.

**Checkpoint**: US5 complete.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [x] T044 Update `docs/configuration.md` — add `roles-mapping` properties, deprecation notice for `allowedRoles`.
- [x] T045 Run `./gradlew checkstyleMain checkstyleTest` — all files pass.
- [x] T046 Run `./gradlew testFast` — full fast test suite passes.
- [ ] T047 Run `./gradlew clean build` — full clean build passes.

---

## Dependencies & Execution Order

- **Phase 1** → **Phase 2** → **Phase 3** (US1+2+3) and **Phase 4** (US5) can run in parallel → **Phase 5**
- Tasks marked [P] within each phase can run in parallel
