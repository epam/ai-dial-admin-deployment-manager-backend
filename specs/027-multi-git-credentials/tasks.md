# Tasks: Multiple Git Credentials per Domain

**Input**: Design documents from `/specs/027-multi-git-credentials/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/trusted-private-repos-schema.md, quickstart.md

**Tests**: Included as regular (non-optional) tasks. This project's constitution mandates unit-test coverage for behavior changes (Testing Conventions) and gates PR-readiness on `./gradlew clean build`, so test tasks are not treated as an optional TDD extra here — they're part of each story's definition of done.

**Organization**: Tasks are grouped by user story per spec.md priorities (P1/P2/P3). The credential-resolution algorithm itself is a single shared mechanism that all three stories exercise through different scopes, so it is built once in Foundational (Phase 2); each User Story phase then adds the targeted test coverage proving that story's acceptance scenarios hold, which is also where each story's real independent-test value shows up.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- File paths are exact and relative to the repository root

## Path Conventions

Single project (existing Spring Boot backend). All paths under `src/main/java/com/epam/aidial/deployment/manager/` and `src/test/java/com/epam/aidial/deployment/manager/`, per plan.md's Project Structure — no new modules.

---

## Phase 1: Setup

**Purpose**: Establish a clean baseline before touching shared git-credential config/matching code.

- [X] T001 Run `./gradlew testFast --tests "com.epam.aidial.deployment.manager.configuration.*" --tests "com.epam.aidial.deployment.manager.service.pipeline.specification.*"` on branch `027-multi-git-credentials` to confirm a clean baseline before any change (no existing test file changes expected at this point)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extend the config schema, add the shared scope-matching mechanism, and wire it into both validation (fail-fast on ambiguous config) and resolution (pick the most specific credential). This phase delivers the entire credential-resolution engine — every user story below only adds test coverage for a slice of behavior this phase implements.

**⚠️ CRITICAL**: No user story task can be verified until this phase is complete.

- [X] T002 [P] Add optional `path` field (String, nullable, no default) to `GitPropertiesDto.TrustedPrivateGitRepoDto` in `src/main/java/com/epam/aidial/deployment/manager/configuration/GitPropertiesDto.java`
- [X] T003 [P] Add optional `path` field (String, nullable, no default) to `GitProperties.TrustedPrivateGitRepo` in `src/main/java/com/epam/aidial/deployment/manager/configuration/GitProperties.java`
- [X] T004 Create `src/main/java/com/epam/aidial/deployment/manager/configuration/GitScopeUtils.java` with static methods `normalizeHost(String host)` (lowercase, trim) and `normalizePath(String path)` (trim leading/trailing `/`, strip trailing `.git`, return `null` for blank input) — single source of truth for scope normalization (research D2), used by both validation (T006) and resolution (T008) so they can never disagree on what "the same scope" means (depends on: none)
- [X] T005 Wire `path` through `GitConfiguration.convertToProcessedModel()` in `src/main/java/com/epam/aidial/deployment/manager/configuration/GitConfiguration.java`, normalizing it via `GitScopeUtils.normalizePath` at parse time so the processed model always holds an already-normalized value (depends on: T002, T003, T004)
- [X] T006 Add cross-entry duplicate-scope validation to `GitConfiguration` (FR-005, research D4): after parsing all entries, group by `(GitScopeUtils.normalizeHost(host), normalized-path-or-null)`; if any group has more than one entry, throw `IllegalArgumentException` naming the conflicting `host`/`path` and the position of each conflicting entry in the source list, in `src/main/java/com/epam/aidial/deployment/manager/configuration/GitConfiguration.java` (depends on: T004, T005)
- [X] T007 [P] Add `extractPathFromUrl(String gitUrl)` to `GitService` in `src/main/java/com/epam/aidial/deployment/manager/service/pipeline/specification/GitService.java`, mirroring the existing `extractHostFromUrl`/`extractHostFromSshUrl`/`extractHostFromHttpUrl` HTTPS-vs-SSH split (research D5), and normalize the result via `GitScopeUtils.normalizePath` (depends on: T004)
- [X] T008 Replace `GitService.findMatchingTrustedRepo`'s first-match loop with specificity-ranked resolution in `src/main/java/com/epam/aidial/deployment/manager/service/pipeline/specification/GitService.java`: for the URL's `(host, path)`, compute for every configured entry whether it matches (host-exact-or-subdomain per FR-003a; path absent/segment-prefix/exact per FR-002a) and, if so, its specificity tuple `(hostRank, pathRank, pathLength)` (research D3); among matches, select the entry with the best tuple (`hostRank` ascending, then `pathRank` descending, then `pathLength` descending) instead of the first list match (depends on: T004, T007)
- [X] T009 [P] `GitConfigurationTest` in `src/test/java/com/epam/aidial/deployment/manager/configuration/GitConfigurationTest.java`: cover existing per-entry validation rules (unchanged behavior — regression coverage) plus new cases — accepts multiple distinct scopes on one host, rejects two entries with an identical normalized `(host, path)`, accepts a `path` written with a leading/trailing slash or trailing `.git` as equivalent to its normalized form (depends on: T006)

**Checkpoint**: Configuration accepts multiple scoped entries per host, rejects ambiguous (duplicate-scope) config at startup, and `GitService` resolves the single most specific matching credential for any repository URL. User story phases below add targeted verification.

---

## Phase 3: User Story 1 - Repository-specific credentials on a shared domain (Priority: P1) 🎯 MVP

**Goal**: A credential scoped to one exact repository is used for that repository, while other repositories on the same host keep using whatever else applies to them.

**Independent Test**: Configure a repository-specific entry for `git.example.com/team/service-a` plus a domain-wide entry for `git.example.com`; confirm a build against `team/service-a` uses the repository-specific entry and a build against a sibling repo uses the domain-wide one.

### Tests for User Story 1

- [X] T010 [P] [US1] `GitServiceTest` in `src/test/java/com/epam/aidial/deployment/manager/service/pipeline/specification/GitServiceTest.java`: a repository-specific entry is selected over a domain-wide entry on the same host when the build URL matches its exact repository path (spec US1 acceptance scenario 1)
- [X] T011 [P] [US1] `GitServiceTest`: a repository-specific entry configured for one repository does NOT apply to a different repository on the same host — the domain-wide entry is used instead (spec US1 acceptance scenario 2)

**Checkpoint**: User Story 1 independently verified — repository-specific override behavior is correct on its own.

---

## Phase 4: User Story 2 - One token covering every repository in a project (Priority: P2)

**Goal**: A single project/group-scoped credential automatically covers every repository under that path, without per-repository configuration, while still yielding to a more specific repository-level override.

**Independent Test**: Configure a project-scoped entry for `git.example.com/team`; confirm builds against two different repositories under `team/` both use it, and that adding a repository-specific entry for one of them overrides the project-scoped entry for that repository only.

### Tests for User Story 2

- [X] T012 [P] [US2] `GitServiceTest`: a project-scoped entry is selected for a repository under its path when no repository-specific entry exists (spec US2 acceptance scenario 1)
- [X] T013 [P] [US2] `GitServiceTest`: a repository-specific entry still overrides a project-scoped entry for the same repository (spec US2 acceptance scenario 2)
- [X] T014 [P] [US2] `GitServiceTest`: project-scope path matching is segment-boundary-aware — a scope of `team` matches `team/service-a` but does NOT match the unrelated repository `team2/service-a` (FR-002a, spec Edge Cases)

**Checkpoint**: User Stories 1 AND 2 both independently verified.

---

## Phase 5: User Story 3 - Automatic selection of the most specific credential (Priority: P3)

**Goal**: When several configured credentials could apply to the same repository URL, the system always resolves to the single most specific one with no manual ordering, including across exact-host-vs-subdomain and nested project-scope cases.

**Independent Test**: Configure a domain-wide, a project-scoped, and a repository-specific entry together on one host (in arbitrary list order); confirm three builds against repositories matching each scope resolve to the domain-wide, project-scoped, and repository-specific entry respectively.

### Tests for User Story 3

- [X] T015 [P] [US3] `GitServiceTest`: with domain-wide, project-scoped, and repository-specific entries all configured on one host, three builds against repositories matching each scope resolve to the correct entry regardless of the entries' order in configuration (spec US3 acceptance scenario 1)
- [X] T016 [P] [US3] `GitServiceTest`: an exact-host match always wins over a subdomain-inherited (parent-domain) match, even when the subdomain-inherited entry has a more specific path (spec Edge Cases, FR-003, research D3)
- [X] T017 [P] [US3] `GitServiceTest`: nested project-scoped entries (e.g. `group` and `group/subgroup`) resolve to the longer/more specific path prefix (spec Edge Cases)

**Checkpoint**: All user stories independently functional; full precedence hierarchy from spec FR-003 is covered by tests.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, capability-spec scaffolding, and final verification across all stories.

- [X] T018 [P] Update `docs/configuration.md` to document the new `path` field on `TRUSTED_PRIVATE_GIT_REPOS` entries — env var name (unchanged), field purpose, and a short example matching `contracts/trusted-private-repos-schema.md` (constitution: Configuration documentation rule)
- [X] T019 [P] Scaffold `specs/git-credentials/spec.md` (house style, modeled on `specs/api-conventions/spec.md`) documenting the shipped git-credential configuration and scoped-resolution behavior, and add a row for it to `specs/README.md` (CLAUDE.md: Numbered-spec hygiene — this feature's `**Capability**:` field is `N/A — creates new capability git-credentials`)
- [X] T020 Flip `specs/027-multi-git-credentials/spec.md`'s `**Status**:` from `Draft` to `Implemented` (constitution: Numbered-spec lifecycle) — depends on T002-T019 being complete
- [X] T021 Run `./gradlew checkstyleMain checkstyleTest` and fix any violations introduced by T002-T017
- [X] T022 Run `./gradlew testFast` (full fast suite) to confirm no regressions beyond the new/updated tests
- [ ] T023 Manually walk through `quickstart.md` steps 1-6 (multi-scope startup, each story's resolution, duplicate-scope startup failure, and single-entry backward compatibility) against a running instance

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories. This phase implements the entire credential-resolution mechanism (schema + validation + matching algorithm).
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion. Since Foundational already implements the shared resolution engine, the three phases are pure test-coverage additions and can proceed in any order or in parallel.
- **Polish (Phase 6)**: Depends on all user story phases being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational — no dependency on US2/US3.
- **User Story 2 (P2)**: Can start after Foundational — no dependency on US1/US3 (independently testable even though it exercises the same underlying engine as US1).
- **User Story 3 (P3)**: Can start after Foundational — no dependency on US1/US2.

### Within Foundational

T002/T003 (parallel field additions) → T004 (shared normalization util) → T005 (wiring) → T006 (validation) → T009 (validation tests); T004 → T007 (path extraction) → T008 (resolution algorithm). T008 and T006 can proceed in parallel once T004/T005/T007 are done.

### Parallel Opportunities

- T002 and T003 (different files) can run in parallel.
- T007 can run in parallel with T005/T006 (different file, only depends on T004).
- All `GitServiceTest` tasks within a single user story phase (T010/T011, T012/T013/T014, T015/T016/T017) are additions to the same test file but cover independent scenarios — write them as separate test methods; they don't block each other logically even though they land in one file.
- T018 and T019 (different files, Polish phase) can run in parallel.

---

## Parallel Example: Foundational Phase

```bash
# Launch the two independent field additions together:
Task: "Add optional path field to GitPropertiesDto.TrustedPrivateGitRepoDto in src/main/java/com/epam/aidial/deployment/manager/configuration/GitPropertiesDto.java"
Task: "Add optional path field to GitProperties.TrustedPrivateGitRepo in src/main/java/com/epam/aidial/deployment/manager/configuration/GitProperties.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (CRITICAL — this is where the actual mechanism is built).
3. Complete Phase 3: User Story 1 tests (T010-T011).
4. **STOP and VALIDATE**: run `GitServiceTest`, confirm US1's independent test passes.
5. This alone already delivers the P1 value (repository-specific credentials coexisting with a domain-wide fallback).

### Incremental Delivery

1. Setup + Foundational → the full resolution engine exists and is validated for duplicate scopes.
2. Add User Story 1 tests → confirms repository-specific override (MVP).
3. Add User Story 2 tests → confirms project-scoped coverage and segment-boundary matching.
4. Add User Story 3 tests → confirms full precedence hierarchy (subdomain tie-break, nested scopes).
5. Polish → docs, new capability spec scaffold, status flip, final checkstyle/test/quickstart pass.

### Notes

- Because the resolution algorithm is a single indivisible mechanism (it can't correctly handle repository-specific matching without also correctly handling the project/domain fallback tiers it's ranked against), the "incremental" delivery here is really about test coverage depth, not staged functionality — this is expected for a feature this small and is called out explicitly rather than forcing an artificial functional split.
- Commit after each phase (or logical group within Foundational) rather than after every single task, given how tightly T002-T008 are coupled.
- Avoid: adding a `scopeType` enum or other discriminator field (see research D1) — scope kind is derived from path comparison at resolution time, not stored.
