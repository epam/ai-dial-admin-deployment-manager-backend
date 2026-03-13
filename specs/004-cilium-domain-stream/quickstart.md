# Quickstart: Cilium Domain Access Streaming (004-cilium-domain-stream)

**Feature**: 004-cilium-domain-stream  
**Date**: 2025-03-13

## Prerequisites

- Java 21, Gradle 8.13.
- Cilium with Hubble Relay deployed in the cluster (e.g. `hubble-relay.cilium.svc.cluster.local:80`).
- Image builds that run in pods whose names include the image definition ID (e.g. `dm-base-{imageDefinitionId}-*` in namespace `mcp-build`).

## Build and run (local/dev)

1. **Checkout and build**
   ```bash
   git checkout 004-cilium-domain-stream
   ./gradlew clean build
   ```
   If Hubble protos are added and codegen is wired, the first build will generate gRPC/Java from proto files.

2. **Configuration (optional)**
   - Hubble Relay: set `app.hubble-relay.host` and `app.hubble-relay.port` if not using defaults (`hubble-relay.cilium.svc.cluster.local`, `80`).
   - SSE: existing `app.sse.poll-interval-ms` and `app.sse.min-streaming-interval-ms` apply to the accessed-domains stream as well.
   - Cilium: feature is active when Cilium/Hubble is enabled and the Hubble Relay service is reachable; otherwise the domain stream is unavailable or empty per spec.

3. **Run application**
   ```bash
   ./gradlew bootRun
   ```
   Or run from IDE with the same env/options.

4. **Trigger a build and open streams**
   - `POST /api/v1/images/builds` with `{ "imageDefinitionId": "<uuid>" }`.
   - `GET /api/v1/images/builds/{id}/logs` — build logs (existing).
   - `GET /api/v1/images/builds/{id}/accessed-domains` — domain access stream (new); events `accessed-domains` (domain + verdict) and `status`.
   - `GET /api/v1/images/builds/{id}/details` — includes `accessedDomains` when available.

## Tests

- **Unit**: `./gradlew testFast` (excludes Testcontainers-based DB tests).
- **Full**: `./gradlew test` (includes Postgres/SQL Server Testcontainers where applicable).
- **Checkstyle**: `./gradlew checkstyleMain checkstyleTest`.

Relevant test classes (to be added or extended): `ImageBuildLogsServiceTest` (streamAccessedDomains), `ImageBuildControllerTest` (subscribeToAccessedDomains), `ImageDefinitionService`/repository tests for addAccessedDomains/reset; HubbleRelayService tests (e.g. with mocked gRPC stub).

## Database migrations

After pulling this feature, run the application (or Flyway) so that V1.50 migrations are applied for your vendor (H2, POSTGRES, MS_SQL_SERVER). Column `image_definition.accessed_domains` will be added with default `[]`.

## When Cilium is disabled

- `GET .../accessed-domains` returns 404 or an empty stream (or endpoint is not advertised) per FR-004.
- `GET .../details` may omit `accessedDomains` or return an empty array.

## Next steps

- Implement tasks from `tasks.md` (created by `/speckit.tasks`).
- Verify Hubble Relay connectivity and FlowFilter (source_pod) in your cluster so that build pods are correctly filtered.
