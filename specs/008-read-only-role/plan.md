# Implementation Plan: Read-Only Admin Role

**Branch**: `008-read-only-role` | **Date**: 2026-03-18 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/008-read-only-role/spec.md`

## Summary

Add FULL_ADMIN and READ_ONLY_ADMIN application roles to the deployment manager, following the pattern established in `ai-dial-admin-backend` PR #761. Identity provider roles are mapped to application roles via a new `roles-mapping` configuration property. Mutating controller endpoints are annotated with `@FullAdminOnly` (meta-annotation for `@PreAuthorize("hasAuthority('FULL_ADMIN')")`). A new `/api/v1/security-info` endpoint exposes user roles for frontend UI adaptation. Backward compatibility with existing `allowedRoles` configuration is preserved.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Spring Security + OAuth2 Resource Server, Lombok, MapStruct 1.6.0, SpringDoc OpenAPI 2.8.5
**Storage**: N/A (no database changes; roles resolved at authentication time from token claims + config)
**Testing**: JUnit 5, AssertJ, MockMvc, spring-security-test, io.jsonwebtoken:jjwt
**Target Platform**: Linux server (Kubernetes)
**Project Type**: Web service (REST API)
**Performance Goals**: N/A (role resolution is in-memory, negligible overhead)
**Constraints**: Must maintain backward compatibility with existing `allowedRoles` configuration
**Scale/Scope**: 7 controllers with mutating endpoints, ~15 methods to annotate, ~8 new files, ~12 modified files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|---|---|---|
| Strict layered architecture | PASS | `@FullAdminOnly` is web-layer annotation; `RolesMappingResolver` is a `@Component` in `web/security/`; `UserRolesResolver` is a plain class created by factories in `web/security/` |
| Transactional discipline | PASS | No `@Transactional` needed — pure auth/config feature |
| Kubernetes isolation | PASS | No K8s API interaction |
| Observability first | PASS | `@LogExecution` on `SecurityInfoController`, `RolesMappingResolver`; existing logging in converters maintained |
| Security by configuration | PASS | Extends existing security config pattern; new properties follow established structure |
| Naming conventions | PASS | `SecurityInfoController`, `SecurityInfoDto`, `UserInfoDto`, `FullAdminOnly`, `UserRole`, `RolesMappingResolver`, `UserRolesResolver` |
| Code style | PASS | Google Java Style, 180-char lines, no wildcards |
| API conventions | PASS | `/api/v1/security-info` follows base path |
| Testing conventions | PASS | MockMvc security tests, `shouldDoX`/`shouldFailDoX_whenY` naming |
| Configuration documentation | PASS | Task included to update `docs/configuration.md` |
| Anti-patterns | PASS | No violations |

**Post-Phase 1 re-check**: All gates still PASS. No new patterns or dependencies introduced.

## Project Structure

### Documentation (this feature)

```text
specs/008-read-only-role/
├── plan.md              # This file
├── spec.md              # Feature specification
├── data-model.md        # Phase 1 data model
├── contracts/
│   ├── security-info.md # Security info endpoint contract
│   └── authorization.md # Authorization behavior contract
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── dto/
│   ├── SecurityInfoDto.java              # NEW
│   └── UserInfoDto.java                  # NEW
├── service/security/
│   └── SecurityClaimsExtractor.java      # MODIFIED (add getRoles)
├── web/controller/
│   ├── SecurityInfoController.java       # NEW
│   ├── DeploymentController.java         # MODIFIED (@FullAdminOnly)
│   ├── ConfigController.java             # MODIFIED (@FullAdminOnly)
│   ├── ImageDefinitionController.java    # MODIFIED (@FullAdminOnly)
│   ├── ImageBuildController.java         # MODIFIED (@FullAdminOnly)
│   ├── DisposableResourceController.java # MODIFIED (@FullAdminOnly)
│   └── GlobalDomainWhitelistController.java # MODIFIED (@FullAdminOnly)
├── web/handler/
│   └── DefaultExceptionHandler.java      # MODIFIED (AccessDeniedException → 403)
└── web/security/
    ├── FullAdminOnly.java                # NEW
    ├── UserRole.java                     # NEW
    ├── RolesMappingResolver.java         # NEW
    ├── UserRolesResolver.java            # NEW
    ├── PublicPathsResolver.java          # NEW (extracted from SecurityConfiguration)
    ├── NimbusJwtDecoderResolver.java     # NEW (interface for JWT decoder creation)
    ├── BasicSecurityConfiguration.java   # NEW (basic auth mode)
    ├── OidcSecurityConfiguration.java    # RENAMED from SecurityConfiguration.java
    ├── NoneSecurityConfiguration.java    # RENAMED from NoSecurityConfiguration.java
    ├── IdentityProvidersProperties.java  # MODIFIED (rolesMapping as JSON String, ObjectMapper, validation)
    ├── IdentityProviderUtils.java        # MODIFIED (RolesMappingResolver + ObjectMapper, getRolesMapping())
    ├── JwtProviderConfig.java            # MODIFIED (from() takes 3 params)
    ├── OpaqueTokenProviderConfig.java    # MODIFIED (from() takes 3 params)
    ├── JwtAuthenticationConverter.java   # MODIFIED (use UserRolesResolver, early return)
    ├── OpaqueAuthenticationConverter.java # MODIFIED (use UserRolesResolver)
    ├── JwtAuthenticationConverterFactory.java  # MODIFIED (use identityProviderUtils.getRolesMapping())
    ├── OpaqueAuthenticationConverterFactory.java # MODIFIED (use identityProviderUtils.getRolesMapping())
    ├── IssuerToDecoderMapFactory.java    # MODIFIED (use NimbusJwtDecoderResolver)
    └── TokenDecoderFactoryImpl.java      # MODIFIED (delegate decoder creation)

src/main/resources/
└── application.yml                       # MODIFIED (add roles-mapping)

docs/
└── configuration.md                      # MODIFIED (document roles-mapping)

src/test/java/com/epam/aidial/deployment/manager/
├── utils/
│   └── IdentityProviderTestHelper.java       # MODIFIED (simplified, no rolesMapping)
├── web/controller/
│   ├── none/
│   │   └── SecurityInfoControllerTest.java   # NEW
│   └── oidc/
│       ├── AbstractControllerSecurityTest.java   # MODIFIED (provider config via @TestPropertySource)
│       └── ImageDefinitionControllerSecurityTest.java # MODIFIED (expanded security tests)
└── web/security/
    ├── TestSecurityConfig.java               # NEW (replaces TestTokenDecoderFactory + TestAuthenticationConverterFactory + TestIdentityProviderConfig)
    ├── RolesMappingResolverTest.java         # NEW
    └── UserRolesResolverTest.java            # NEW

DELETED:
├── utils/TestAuthenticationConverterFactory.java
├── utils/TestIdentityProviderConfig.java
└── utils/TestTokenDecoderFactory.java
```

**Structure Decision**: All new code follows the existing package structure. Security classes go in `web/security/`, DTOs in `dto/`, controller in `web/controller/`. No new packages needed.

## Complexity Tracking

No constitution violations. No complexity justifications needed.
