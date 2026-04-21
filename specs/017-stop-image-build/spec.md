# Feature Specification: Stop Image Build

**Feature Branch**: `017-stop-image-build`  
**Created**: 2026-04-20  
**Status**: Draft  
**Input**: User description: "Currently, there is no ability to interrupt image build process - this feature should be implemented."

## Clarifications

### Session 2026-04-20

- Q: When the cluster-side build resources cannot be deleted, what should the stop action do? → A: All-or-nothing — stop returns an error, recorded status stays `BUILDING`, admin retries.
- Q: When a stop request arrives for a build that just reached a terminal state, how should the stop action respond? → A: Reject with the same "build is not in progress" error as other non-running states; include the current terminal state in the error payload so the client can refresh its view.
- Q: What canonical name should the new terminal build status use? → A: `BUILD_STOPPED` — used for the enum value, persistence value, API-visible status, and audit event terminology.
- Q: Is auto-stopping an in-progress build when its image definition is deleted part of this feature? → A: Out of scope. This feature covers only the explicit administrator-initiated stop action; delete-while-building semantics stay as-is and are deferred to a follow-up.
- Q: Should the stop request accept an optional administrator-provided reason/comment? → A: No reason field. The stop action takes no body beyond identifying the target build; the audit entry records who and when only.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Stop a running image build (Priority: P1)

An administrator triggers a build for an image definition and later realizes the build should not continue — the source was wrong, the build is hung consuming cluster resources, the underlying image definition needs to be corrected, or they simply need to reclaim capacity. Today the administrator has no way to intervene: the build runs until it either succeeds or fails on its own. The administrator needs a way to explicitly interrupt an in-progress build so that the build stops promptly, cluster resources are released, and the image definition returns to a state from which a corrected build can be retriggered.

**Why this priority**: This is the entire feature. Without it there is no value delivered. Runaway or mistaken builds currently block administrator workflows (a new build cannot be started while one is in progress) and waste cluster capacity until the build times out on its own.

**Independent Test**: Start a build for an image definition, observe it enter the in-progress state, invoke the new stop action for that image definition, and confirm that the build transitions out of the in-progress state within a short interval, that cluster-side build resources are cleaned up, and that a new build can subsequently be triggered for the same image definition.

**Acceptance Scenarios**:

1. **Given** an image definition with a build currently in progress, **When** the administrator requests the build to be stopped, **Then** the build is interrupted, its status is updated to reflect that it was stopped by user action, and any streaming clients observing the build receive a final status event and their streams close.
2. **Given** an image definition with a build currently in progress, **When** the build is stopped, **Then** the underlying execution resources (the cluster-side build job and any associated pods) are removed so that cluster capacity is released.
3. **Given** an image definition whose previous build was stopped by the administrator, **When** the administrator triggers a new build for the same image definition, **Then** the new build is accepted and starts normally, just as it would after any other terminal state.

---

### User Story 2 - Reject stop requests for builds that are not in progress (Priority: P2)

When there is no active build to interrupt, the stop action must not have destructive side effects. An administrator (or a retrying client) may send a stop request for an image definition whose build has already completed, already failed, already been stopped, or was never started. The system must recognize these cases and respond in a way that makes clear no action was taken, without altering the image definition's recorded state or history.

**Why this priority**: Protects the data integrity of completed/terminal builds and provides predictable idempotent behavior so client UIs and automations can safely retry or fire the stop action without worrying about corrupting a finished build's record.

**Independent Test**: Attempt to stop a build in each non-running state (never built, completed successfully, failed, already stopped) and verify the system refuses each attempt with a clear error and leaves the existing build record untouched.

**Acceptance Scenarios**:

1. **Given** an image definition whose build is in a terminal state (succeeded, failed, or already stopped), **When** a stop request is submitted, **Then** the request is rejected with an error message indicating that the build is not in progress, and the recorded build status, logs, built-at timestamp, and image name remain unchanged.
2. **Given** an image definition that has never been built, **When** a stop request is submitted, **Then** the request is rejected with an error message indicating that there is no build to stop.
3. **Given** an image definition that does not exist, **When** a stop request is submitted, **Then** the request is rejected as not found.

---

### User Story 3 - Observers are notified promptly when a build is stopped (Priority: P3)

Administrators who opened the real-time status or log stream for a build should see the stop outcome immediately, rather than waiting for a timeout or having to refresh. When a build is interrupted by the stop action, any active status stream must deliver a final status event reflecting the stopped outcome and close, and any active log stream must flush any remaining captured log lines followed by the final status event and then close.

**Why this priority**: Quality-of-life improvement on top of P1 — the core stop behavior works without it, but without it the UI feedback loop is unclear and administrators may not realize the stop took effect.

**Independent Test**: Open the status stream and the log stream for a running build, invoke stop, and confirm that each open stream delivers a final status event reflecting the stopped outcome and closes on its own, within the same short interval as P1.

**Acceptance Scenarios**:

1. **Given** an administrator has an open real-time status stream for an in-progress build, **When** the build is stopped, **Then** the stream delivers a final status event indicating the stopped terminal state and closes.
2. **Given** an administrator has an open real-time log stream for an in-progress build, **When** the build is stopped, **Then** any remaining captured log output is delivered, followed by a final status event indicating the stopped terminal state, and the stream closes.

---

### Edge Cases

- **Stop requested as the build is completing**: The build naturally reaches success or failure at approximately the same moment a stop request arrives. The terminal states are deterministic under the row-level lock: whoever commits their status write last wins. If the natural completion commits first, the stop request is rejected with the same "build is not in progress" error that applies to any other non-running state (see FR-002), and the error payload MUST include the build's actual current terminal state so the client can refresh its view without a follow-up request. In the reverse narrow race — stop commits first but the pipeline's own Job had already succeeded (image is physically in the registry) — the pipeline's subsequent success write overwrites `BUILD_STOPPED` with `BUILD_SUCCESSFUL` so that the built artifact is adopted rather than orphaned. Observers that were streaming status see `BUILD_STOPPED` and their stream closes; they must re-query REST to learn of the late adoption. This narrow window is acceptable because the admin's intent to reclaim capacity is already satisfied (the Job is gone) and no cluster-side state is left dangling.
- **Cluster-side resource already gone**: The execution resources for the build have already been removed by an external actor or by a prior cleanup. The stop action still completes successfully from the user's perspective — the recorded status is moved to the stopped terminal state and a fresh build can be retriggered.
- **Cluster-side deletion fails**: Removing the execution resources returns an error. The stop action fails as a whole — the recorded build status remains in the in-progress state, the administrator receives an error response, and the administrator can retry the stop action. The status is never advanced to the stopped terminal state until cluster-side cleanup has succeeded, so the recorded status and the real cluster state can never diverge.
- **Stop requested twice in quick succession**: Two stop requests arrive back-to-back for the same in-progress build. The first one performs the work; the second one must be handled by the same rules as Story 2 (rejected because the build is no longer in progress) without any additional side effects.
- **Logs captured at the moment of interruption**: Log output produced by the build up to the point of interruption should be preserved on the build record so the administrator can still inspect what the build was doing when it was stopped.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide an administrator-accessible action to stop an in-progress image build, identified solely by the image definition the build belongs to. The stop action MUST NOT require or accept any additional input from the caller (no reason, no comment, no flags) — the image definition identifier alone is the complete contract.
- **FR-002**: The system MUST accept the stop action only when the targeted build is currently in progress; stop requests for builds in any terminal state (succeeded, failed, already stopped) or for image definitions that have never been built MUST be rejected with an informative error and MUST NOT modify the existing build record. The error payload MUST include the build's current recorded status so that callers who lost a race with natural completion can reconcile their view without an extra round-trip.
- **FR-003**: The system MUST reject stop requests for image definitions that do not exist with a not-found error.
- **FR-004**: When the stop action is accepted, the system MUST remove the cluster-side execution resources associated with the build (the build job and any pods it manages) so that cluster capacity is released. If the cluster-side removal fails, the entire stop action MUST fail with an error, the recorded build status MUST remain in the in-progress state, and the administrator MUST be able to retry.
- **FR-005**: When the stop action is accepted AND cluster-side cleanup has succeeded, the system MUST record a terminal build status named `BUILD_STOPPED`, distinct from `BUILD_SUCCESSFUL` and `BUILD_FAILED`, so that administrators and downstream consumers can recognize a build that was intentionally interrupted (as opposed to one that failed on its own or completed). The same canonical name `BUILD_STOPPED` MUST be used for the persistence value, the API-visible status string, and the audit event terminology. The status MUST NOT be advanced to `BUILD_STOPPED` before cluster-side cleanup has confirmed success. Once recorded, `BUILD_STOPPED` is terminal except in the narrow race described in Edge Cases, where a pipeline whose Job already succeeded may commit its own success write after stop's — see "Stop requested as the build is completing".
- **FR-006**: The system MUST preserve any log output that was captured up to the moment the build was interrupted, so that the administrator can still inspect what the build was doing when it was stopped.
- **FR-007**: The system MUST allow a new build to be triggered for an image definition whose previous build was stopped, using the existing build-triggering flow without additional workarounds.
- **FR-008**: The system MUST restrict the stop action to the same administrator role that is authorized to trigger builds today; non-privileged users MUST be rejected with an authorization error.
- **FR-009**: The system MUST ensure that any open real-time status streams for the build deliver a final status event reflecting the stopped terminal state and then close, consistent with how other terminal states close such streams today.
- **FR-010**: The system MUST ensure that any open real-time log streams for the build flush any remaining captured log output, deliver a final status event reflecting the stopped terminal state, and then close.
- **FR-011**: The system MUST record the stop action in the audit log in a form consistent with other build lifecycle events, so that administrators can review who stopped which build and when. The audit entry MUST capture at minimum the acting administrator's identity, the target image definition, and the timestamp; no additional free-text reason is required or stored.
- **FR-012**: The system MUST make the stop action idempotent in effect: repeated stop requests for the same build MUST NOT cause additional side effects beyond the first one that succeeded, and subsequent requests MUST be rejected by the "build is not in progress" rule from FR-002.

### Key Entities *(include if feature involves data)*

- **Image definition**: The existing entity that owns the build record. Its recorded build status is what this feature transitions into a new "stopped" terminal state when the stop action succeeds.
- **Build status**: The existing lifecycle value attached to an image definition. This feature adds one additional terminal value, `BUILD_STOPPED`, representing "stopped by administrator", alongside the existing `NOT_BUILT`, `BUILDING`, `BUILD_FAILED`, and `BUILD_SUCCESSFUL` values.
- **Build execution resource**: The cluster-side job (and its pods) that performs the actual build. This feature treats it as something the stop action removes on behalf of the administrator.
- **Build log record**: The captured output of the build, stored alongside the image definition. This feature preserves whatever has been captured up to the interruption point.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Administrators who issue the stop action either observe the build leave the in-progress state or receive a clear failure response before the operator-configurable stop-timeout elapses, in at least 95% of cases.
- **SC-002**: After a stopped build, administrators can start a new build for the same image definition on the first attempt without any manual cleanup steps in 100% of cases.
- **SC-003**: Stop requests submitted for builds that are not in progress are rejected without any change to the recorded build status, captured logs, built-at timestamp, or image name in 100% of cases.
- **SC-004**: Real-time status and log streams that are open when a build is stopped deliver a final status event reflecting the stopped outcome and close on their own, with no need for administrators to refresh or reconnect, in at least 95% of cases.
- **SC-005**: Cluster-side build resources (jobs and pods) associated with a stopped build are removed and no longer consume capacity within 60 seconds of a successful stop action in at least 95% of cases.
- **SC-006**: Every successful stop action is retrievable from the audit log and correctly attributed to the administrator who performed it.

## Assumptions

- The stop action is available only to the same administrator role that is authorized to trigger builds today (consistent with the existing build endpoints); there is no separate "build operator" role being introduced by this feature.
- Builds continue to be identified by their image definition; this feature does not introduce a separate per-attempt build identifier. Each image definition has at most one active build at a time, which matches today's behavior.
- The existing real-time status and log delivery mechanism is reused; this feature does not change how streams are opened or subscribed to, only that a new terminal state is one of the final events they can emit.
- Audit logging infrastructure introduced by the recently delivered auditing feature is reused; this feature simply adds one more lifecycle event to the existing audit categories.
- Stopping a build does not purge its captured logs; administrators may still inspect logs of stopped builds via the existing details/log-retrieval flows.
- All image definition types and all build pipelines in use today (git-sourced Dockerfile builds, existing-image wrapper builds, image copies) are in scope; the stop action is expected to work uniformly regardless of which pipeline is running.
- Out of scope: auto-stopping an in-progress build when its image definition is deleted via the existing delete flow. This feature only covers the explicit administrator-initiated stop action. Delete-while-building semantics remain as they are today and are deferred to a follow-up feature, which can reuse the `BUILD_STOPPED` primitive introduced here.
