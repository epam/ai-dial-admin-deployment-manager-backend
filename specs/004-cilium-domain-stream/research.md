# Research: Cilium Domain Access Streaming

**Feature**: 004-cilium-domain-stream  
**Phase**: 0  
**Date**: 2025-03-13

## 1. Hubble gRPC API and Protobuf Definitions

### Decision

Use Cilium Hubble Observer gRPC API: **GetFlows** streaming RPC. Protobuf definitions are taken from the official Cilium repository (`cilium/cilium`, `api/v1/`). Generate Java classes via Gradle protobuf plugin (e.g. `com.google.protobuf` or `org.xolstice.protobuf-gradle-plugin`) from the following proto files:

- **observer.proto**: `https://raw.githubusercontent.com/cilium/cilium/master/api/v1/observer/observer.proto`  
  - Defines `Observer` service and `GetFlows(GetFlowsRequest) returns (stream GetFlowsResponse)`.
- **flow.proto**: `https://raw.githubusercontent.com/cilium/cilium/master/api/v1/flow/flow.proto`  
  - Defines `Flow`, `FlowFilter`, `Verdict` (e.g. `FORWARDED`, `DROPPED`), `DNS` (field `query` for the domain).
- **relay.proto**: Required by observer.proto; same repo path `api/v1/relay/relay.proto`.
- **Google well-known types**: `google/protobuf/timestamp.proto`, `any.proto`, `wrappers.proto`, `field_mask.proto` — use standard protobuf distribution or dependency.

### Rationale

- Hubble’s gRPC API is the stable way to consume flow data; Cilium docs and repo reference it as the supported interface.
- GetFlows returns structured `Flow` messages (no need to parse human-readable log lines); `Flow` has `Verdict` and `DNS.query`, which map directly to “allowed/blocked” and “domain”.
- Proto files are versioned in Cilium’s main branch; copying them (or pulling via git submodule / build script) keeps generation reproducible.

### Alternatives considered

- **Parsing Cilium agent log text**: Rejected; Hubble gRPC provides structured Flow/DNS and Verdict, avoiding brittle regex on log format.
- **Using a pre-built Hubble Java client**: No official Java client was found; generating from official protos is the standard approach.

---

## 2. Java Code Generation from Protobuf

### Decision

Add a Gradle plugin to compile `.proto` files to Java and generate gRPC stubs (e.g. **protobuf-gradle-plugin** or **protoc** + **grpc-java** codegen). Place Cilium proto files under `src/main/proto` (or a dedicated `proto/` directory) with the same package layout as upstream (`api/v1/observer/`, `api/v1/flow/`, `api/v1/relay/`). Configure the plugin to output generated sources into `build/generated/sources/proto` and add this source set to the main compilation. Use **grpc-java** and **protobuf-java** versions compatible with the project’s existing gRPC usage (e.g. `grpc-netty-shaded:1.75.0`).

### Rationale

- Constitution and existing build use Gradle; keeping codegen in Gradle keeps one build system and avoids external scripts.
- In-tree proto layout preserves import paths and avoids patching Cilium’s imports.

### Alternatives considered

- **Checking in pre-generated Java**: Rejected; regenerating from source preserves clarity and upgrade path.
- **Fetching protos at build time (e.g. from GitHub)**: Acceptable; research assumes either in-repo copy or a single fetch step so builds are reproducible.

---

## 3. Hubble Relay Connection and Filtering

### Decision

- **Endpoint**: Hubble Relay at `hubble-relay.cilium.svc.cluster.local:80` (plaintext gRPC). Make host and port configurable (e.g. `app.hubble-relay.host`, `app.hubble-relay.port`) for tests and different environments.
- **Filtering**: Use `GetFlowsRequest` with `follow = true` and `whitelist` containing one `FlowFilter`. Set `FlowFilter.source_pod` to a list of prefixes that match the build pod(s) for the given image definition. Build job pods are named with the image definition ID (e.g. `dm-base-{imageDefinitionId}-*` in namespace `mcp-build`). Use a filter such as `mcp-build/dm-base-{imageDefinitionId}` (or the appropriate namespace and prefix for the cluster) so that only flows from that build are consumed. Rely on `Flow.Verdict` (FORWARDED = allowed, DROPPED = blocked) and `Flow.Dns.Query` for domain; no parsing of log text.

### Rationale

- Filtering by source pod (and optionally namespace) is the intended use of `FlowFilter` and minimizes irrelevant flows.
- `imageDefinition.getId()` uniquely identifies the build; pod naming convention includes this ID, so it is sufficient for the filter.

### Alternatives considered

- **Filtering by FQDN or DNS query only**: Would mix domains from other pods; source_pod (with image definition ID) is required to scope to a single build.

---

## 4. Mapping Flow to Domain Entry and Aggregation

### Decision

- **Per-flow mapping**: For each `GetFlowsResponse` that contains a `Flow` with L7 DNS (e.g. `flow.hasDns()`): domain = `flow.getDns().getQuery()` (normalize: strip trailing dot if present); verdict = `flow.getVerdict() == Verdict.FORWARDED` → ALLOWED, else (e.g. DROPPED) → BLOCKED.
- **Aggregation**: Maintain in-memory a map (domain → verdict) for the current build: if any flow for that domain is FORWARDED, the stored verdict for that domain is ALLOWED; otherwise BLOCKED. When adding a new flow, update the map; then append or update a single **AccessedDomain** (domain, verdict) for that distinct domain and persist via `ImageDefinitionService.addAccessedDomain(s)` so the entity stores one entry per distinct domain with the spec rule “allowed if any access was allowed, otherwise blocked.”

### Rationale

- Spec requires one entry per distinct domain with outcome “allowed if any access was allowed, otherwise blocked”; in-memory aggregation then batch-update matches that and avoids duplicate domain rows.

### Alternatives considered

- **Appending every flow as a separate row**: Rejected; spec explicitly requires one entry per distinct domain with the stated outcome rule.

---

## 5. When to Start/Stop Hubble Streaming and Error Handling

### Decision

- **Start**: When the image build starts (same lifecycle as build logs), start the Hubble GetFlows stream for the build’s pod filter (using the image definition ID). This implies the HubbleRelayService (or a dedicated “domain collector”) is invoked at the beginning of the pipeline (e.g. when the first step that runs in a pod starts) and is given the image definition ID and pod namespace/name pattern.
- **Stop**: When the build reaches a final status (success or failure), stop the GetFlows stream and close the gRPC channel. If Cilium/Hubble becomes unavailable during the stream (e.g. gRPC error, relay unreachable), close the domain stream and signal an error state per FR-008 (stream ends with error; admin can retry or use build logs).
- **Replay**: On client reconnect, resend domain entries that were already delivered (from the persisted `accessed_domains` on the entity), then continue with new ones — per FR-007; order of entries is not guaranteed per spec.

### Rationale

- Aligning start/stop with build logs keeps one clear lifecycle and avoids leaking streams.
- Spec and FR-008 require closing and signaling error when Cilium/reporting is unavailable.

---

## 6. Summary Table

| Topic | Decision | Source |
|-------|----------|--------|
| Hubble API | Observer.GetFlows stream | Cilium api/v1/observer/observer.proto |
| Proto source | cilium/cilium api/v1/*.proto | GitHub raw URLs above |
| Java codegen | Gradle protobuf + gRPC plugin | Project standard |
| Filtering | GetFlowsRequest.whitelist FlowFilter.source_pod = e.g. "mcp-build/dm-base-{id}" | flow.proto FlowFilter |
| Domain / verdict | Flow.Dns.Query, Flow.Verdict → domain + ALLOWED/BLOCKED | flow.proto Flow, DNS, Verdict |
| Aggregation | In-memory map domain→verdict; one row per distinct domain | Spec FR-003, clarifications |
| Start/stop | With build lifecycle; close on final status or Hubble error | User story, FR-008 |
