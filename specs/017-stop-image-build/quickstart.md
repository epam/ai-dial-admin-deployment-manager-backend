# Quickstart: Stop Image Build

Manual verification recipe for the stop feature. Assumes the service is running locally with a cluster reachable via the configured Fabric8 Client and an admin-capable JWT or basic credential available.

## Prerequisites

- Service running: `./gradlew bootRun`
- Admin token or basic credential exported as `$ADMIN_AUTH`, e.g.:
  ```bash
  export ADMIN_AUTH="Authorization: Bearer <admin-jwt>"
  ```
- An existing image definition whose build will take long enough to interrupt (git-sourced Dockerfile build is ideal; copy/wrapper finish too fast). Export its ID:
  ```bash
  export IMAGE_DEF_ID="<uuid>"
  ```

## Happy path — stop a running build

1. **Trigger a build**

   ```bash
   curl -i -X POST http://localhost:8080/api/v1/images/builds \
     -H "$ADMIN_AUTH" \
     -H 'Content-Type: application/json' \
     -d "{\"imageDefinitionId\":\"$IMAGE_DEF_ID\"}"
   ```

   Expect `HTTP/1.1 201 Created`.

2. **Confirm it's building** (optional — subscribe to status stream)

   ```bash
   curl -N http://localhost:8080/api/v1/images/builds/$IMAGE_DEF_ID/status -H "$ADMIN_AUTH"
   ```

   You should see an SSE event with `data: BUILDING`. Leave this terminal open.

3. **Stop the build**

   ```bash
   curl -i -X DELETE http://localhost:8080/api/v1/images/builds/$IMAGE_DEF_ID \
     -H "$ADMIN_AUTH"
   ```

   Expect `HTTP/1.1 204 No Content` with no body.

4. **Verify**:
   - The SSE stream from step 2 delivers a `data: BUILD_STOPPED` event and closes.
   - `GET /api/v1/images/builds/$IMAGE_DEF_ID/details` shows `"status": "BUILD_STOPPED"` with logs preserved.
   - `kubectl get jobs -n <build-namespace>` no longer lists the build's Job (within ~60s of the 204).

## Not-in-progress rejection

With the build now in `BUILD_STOPPED`, re-run the stop:

```bash
curl -i -X DELETE http://localhost:8080/api/v1/images/builds/$IMAGE_DEF_ID \
  -H "$ADMIN_AUTH"
```

Expect `HTTP/1.1 400 Bad Request` and a JSON body whose `message` matches:

```
Image build is not in progress (current status: BUILD_STOPPED)
```

The DB row is unchanged.

## Not-found rejection

```bash
curl -i -X DELETE http://localhost:8080/api/v1/images/builds/00000000-0000-0000-0000-000000000000 \
  -H "$ADMIN_AUTH"
```

Expect `HTTP/1.1 404 Not Found`.

## Authorization rejection

With a non-admin token `$USER_AUTH`:

```bash
curl -i -X DELETE http://localhost:8080/api/v1/images/builds/$IMAGE_DEF_ID \
  -H "$USER_AUTH"
```

Expect `HTTP/1.1 403 Forbidden`.

## Rebuild after stop (FR-007)

Re-issue the trigger from step 1; expect `201 Created`. The build enters `BUILDING` again and proceeds normally.

## Automated verification

- Functional tests: `./gradlew testFast --tests "com.epam.aidial.deployment.manager.functional.h2.StopImageBuildFunctionalTest"`
- Full multi-vendor: `./gradlew test --tests "*StopImageBuild*"`
- Style: `./gradlew checkstyleMain checkstyleTest`

## Success-criteria check

| Criterion | How to observe |
|---|---|
| **SC-001** (stop → out-of-`BUILDING` or clear failure inside 30s p95) | Measure wall-clock between the `DELETE` request send and either the SSE `BUILD_STOPPED` event or the HTTP 400 response; should be well under the hard-coded `30s` stop timeout in a healthy cluster. |
| **SC-002** (new build succeeds first attempt after stop, no manual cleanup) | After the DELETE returns 204, run the trigger from step 1 — expect 201 with no manual `kubectl delete` in between. |
| **SC-003** (non-running rejects do not mutate record) | Read `GET /details` before and after a 400 rejection; JSON payloads identical. |
| **SC-004** (SSE streams close with final event) | The status stream in step 2 should emit `BUILD_STOPPED` and close — verify with the curl `-N` session disconnecting on its own. |
| **SC-005** (cluster resources removed within 60s) | `kubectl get jobs -n <ns>` on a 10-second cadence after the 204. |
| **SC-006** (audit log records who/when/target) | Query the audit log table for the image definition ID and confirm an entry attributed to the admin who issued the DELETE. |
