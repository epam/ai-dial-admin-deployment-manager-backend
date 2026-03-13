# Feature Specification: Cilium Domain Access Streaming

**Feature Branch**: `004-cilium-domain-stream`  
**Created**: 2025-03-13  
**Status**: Draft  
**Input**: User description: "If Cilium is enabled, the system should support streaming a list of all domains accessed during the image build process, in real time and in a manner similar to image build log streaming, but use new stream to separate accessed domains and image build logs. Each domain entry should indicate whether access was allowed or blocked by Cilium, enabling administrators to review domain usage and identify any blocked domains."

## Clarifications

### Session 2025-03-13

- Q: Should each domain access attempt be a separate stream entry, or should entries be aggregated by domain (and if so, how is outcome determined)? → A: One entry per distinct domain with outcome — one row per domain; outcome is allowed if any access was allowed, otherwise blocked.
- Q: On stream reconnection, should the system support replay of missed entries or only deliver new entries? → A: Replay missed entries after reconnection — when the client reconnects, the system resends domain entries that were delivered while the client was disconnected, then continues with new ones.
- Q: If Cilium (or its reporting) becomes unavailable during a build, what should happen to the domain stream? → A: Close the stream and signal an error — the domain stream ends with an error state; the administrator must rely on build logs or retry.
- Q: When the stream is open but no domains have been accessed (yet or ever), should the system send an explicit "no domain access" state? → A: Explicit state only when build ends with zero domains — if the build finishes and no domain was ever accessed, then indicate "no domain access"; during build, no special message.
- Q: Should domain entries in the stream have a defined order (e.g. by first access or last update), or no ordering guarantee? → A: No ordering guarantee — entries are delivered as available; no defined order for distinct domains.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View real-time domain access stream during image build (Priority: P1)

When an administrator monitors an image build and Cilium is enabled, they can open a dedicated stream that shows each domain accessed by the build as it happens. The stream is separate from the build log stream so domain access information does not mix with build output. Administrators can use this to see which domains the build is contacting in real time.

**Why this priority**: Core value of the feature; without a real-time separate stream, administrators cannot observe domain access during the build.

**Independent Test**: Start an image build with Cilium enabled, open the domain access stream, and verify that domain entries appear as the build runs and that the same view does not show build log lines.

**Acceptance Scenarios**:

1. **Given** Cilium is enabled and an image build is in progress, **When** the administrator opens the domain access stream for that build, **Then** the system delivers domain access entries in real time on a dedicated stream distinct from the build log stream.
2. **Given** an image build with Cilium enabled has not started, **When** the administrator opens the domain access stream for that build, **Then** the stream is available and shows entries as soon as the build starts accessing domains.
3. **Given** the administrator is viewing the build log stream, **When** they view the domain access stream in parallel, **Then** build log content and domain access entries are shown in separate streams and do not interleave.

---

### User Story 2 - See whether each domain was allowed or blocked by Cilium (Priority: P2)

For each domain shown in the domain access stream, the administrator can see whether Cilium allowed or blocked the access. This allows quick identification of blocked domains and review of which domains the build is allowed to use.

**Why this priority**: Essential for the stated goal of reviewing domain usage and identifying blocked domains; depends on the stream existing (P1).

**Independent Test**: Trigger an image build that accesses both allowed and blocked domains (per Cilium policy), open the domain access stream, and verify that each entry clearly indicates allowed or blocked.

**Acceptance Scenarios**:

1. **Given** the domain access stream is open for a build and the build accesses a domain that Cilium allows, **When** the entry is delivered, **Then** the entry indicates that access was allowed.
2. **Given** the domain access stream is open for a build and the build accesses a domain that Cilium blocks, **When** the entry is delivered, **Then** the entry indicates that access was blocked.
3. **Given** an administrator is reviewing the stream, **When** they look at any domain entry, **Then** they can unambiguously determine whether that access was allowed or blocked by Cilium.

---

### User Story 3 - No domain stream when Cilium is disabled (Priority: P3)

When Cilium is not enabled for the environment or the build, the system does not expose or populate the domain access stream. Administrators are not shown a domain stream in this case, avoiding confusion or empty streams.

**Why this priority**: Correct behavior and clear boundaries; lower than P1/P2 because it defines when the feature is off rather than core usage.

**Independent Test**: Start an image build with Cilium disabled, attempt to open or observe the domain access stream, and verify that the stream is not offered or remains empty as designed.

**Acceptance Scenarios**:

1. **Given** Cilium is disabled, **When** an administrator views options for an image build, **Then** the domain access stream is not presented as an available stream (or is clearly indicated as unavailable).
2. **Given** Cilium is disabled and a build runs, **When** the administrator checks for domain access data, **Then** no domain access entries are produced for that build.

---

### Edge Cases

- When Cilium is enabled but the build does not access any external domains, the domain stream remains available and delivers no entries during the build; when the build ends with zero domains accessed, the system indicates a clear "no domain access" state so the administrator knows no domains were used.
- When the connection to the domain stream is interrupted and then restored during a build, the system resends domain entries that were delivered while the client was disconnected, then continues delivering new entries; there is no defined order for domain entries in the stream.
- When a build accesses the same domain multiple times, the stream shows one entry per distinct domain; the outcome for that domain is **allowed** if any access was allowed, otherwise **blocked**.
- When the image build ends, the domain stream ends in a predictable way so the administrator knows no further entries will appear.
- When Cilium or its reporting becomes unavailable during a build, the domain stream MUST close and signal an error state so the administrator knows reporting has stopped; they may rely on build logs or retry the build.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When Cilium is enabled, the system MUST provide a dedicated stream for domain access during the image build process, separate from the stream used for image build logs.
- **FR-002**: The domain access stream MUST deliver entries in real time as domains are accessed during the build, in a manner analogous to how build log streaming delivers log lines.
- **FR-003**: Each domain access entry MUST represent one distinct domain, MUST include that domain identifier, and MUST indicate the outcome: **allowed** if any access to that domain was allowed by Cilium, otherwise **blocked**.
- **FR-004**: When Cilium is disabled, the system MUST NOT expose or populate the domain access stream for image builds (or MUST clearly indicate that the stream is unavailable).
- **FR-005**: The system MUST allow administrators to open and consume the domain access stream for a build in parallel with the build log stream without mixing the two streams.
- **FR-006**: The system MUST support administrators in reviewing domain usage and identifying blocked domains from the information provided in the domain access stream.
- **FR-007**: When a client reconnects to the domain access stream during a build, the system MUST resend domain entries that were delivered while the client was disconnected, then continue with new entries; the system does not guarantee any particular order of domain entries in the stream.
- **FR-008**: If Cilium or its reporting becomes unavailable during a build, the system MUST close the domain access stream and signal an error state to the administrator.
- **FR-009**: When a build ends and no domain was ever accessed, the system MUST indicate a clear "no domain access" state on the domain stream before closing it; during the build, no special message is required when no domains have been accessed yet.

### Key Entities

- **Domain access entry**: One record per distinct domain accessed during the build. It includes the domain identifier and the outcome: **allowed** if any access to that domain was allowed by Cilium, otherwise **blocked**. No assumption is made about storage or format.
- **Domain access stream**: A real-time channel of domain access entries for a given image build, logically separate from the build log stream. Order of entries is not guaranteed; entries are delivered as available.

## Assumptions

- Image build and build log streaming already exist; this feature adds a second stream type for domain access when Cilium is in use.
- Cilium (or the surrounding platform) is capable of reporting domain access events and whether each access was allowed or blocked; the system can consume or relay that information for streaming.
- “Real time” means entries are delivered with minimal delay relative to when the build accesses each domain; exact latency targets are a design/implementation concern.
- Administrators have the same or appropriate access to open streams as they do for build logs (no new permission model specified).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Administrators can view a list of all domains accessed during an image build, in real time, without that list being mixed with build log output.
- **SC-002**: For each domain in the stream, administrators can determine within the same view whether access was allowed or blocked by Cilium.
- **SC-003**: Administrators can use the stream to identify blocked domains during or immediately after a build, without needing to inspect build logs for that purpose.
- **SC-004**: When Cilium is disabled, administrators are not presented with or do not receive domain access data for builds, so the feature is clearly scoped to Cilium-enabled builds.
