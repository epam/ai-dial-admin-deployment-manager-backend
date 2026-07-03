# Feature Specification: Automatic Pull Secrets for Trusted-Registry Images

**Feature Branch**: `025-auto-pull-secrets`
**Created**: 2026-06-29
**Status**: Implemented
**Capability**: kubernetes-manifests, deployments, inference-deployments
**Input**: User description: "When a user deploys an image from an external/private registry, the configured trusted registries are not involved at deploy time, so administrators must manually create a docker pull secret and modify the service account. Instead, automatically determine whether credentials exist for the user's image registry, automatically create the pull secret, and wire it into the generated CRD — for image-based deployments, inference services, and transformer images."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Deploy an image-based workload from a trusted private registry (Priority: P1)

An administrator has already configured one or more trusted private registries (host + credentials) once, at system setup. A user then creates and deploys an image-based deployment (MCP server, interceptor, adapter, or application) whose image lives in one of those trusted private registries. The deployment pulls and starts successfully with **no** manual secret creation and **no** manual service-account editing by the administrator.

**Why this priority**: This is the core pain point and the most common case. Image-based deployments are the bulk of what users deploy, and today every private-registry image silently fails to pull until an administrator intervenes by hand. Delivering just this story removes the manual toil for the majority of deployments and is independently shippable.

**Independent Test**: Configure a trusted private registry with valid credentials, create an image-based deployment referencing an image in that registry, deploy it, and confirm the workload reaches a running state without any operator touching Kubernetes secrets or service accounts.

**Acceptance Scenarios**:

1. **Given** a trusted private registry is configured with valid credentials and a user has an image-based deployment whose image host matches that registry, **When** the user deploys it, **Then** the system ensures a pull secret containing those credentials exists in the deployment's namespace and the generated workload references that secret, and the image is pulled successfully.
2. **Given** the same deployment is later undeployed and re-deployed, **When** the deploy runs again, **Then** the system reuses or re-ensures the pull secret idempotently without error and the workload pulls successfully.
3. **Given** a user changes an existing deployment's image to a different trusted private registry, **When** the change is applied, **Then** the generated workload references credentials valid for the new image's registry.

---

### User Story 2 - Deploy an inference service whose transformer image is in a trusted private registry (Priority: P2)

A user deploys an inference model that the system auto-detects as a chained predictor + transformer (text-classification). The transformer container image is operator-supplied and hosted in a trusted private registry. The transformer pod pulls its image successfully without any manual administrator action.

**Why this priority**: Inference deployments with chained transformers are a newer, less frequent path than image-based deployments, but they have the same defect: the transformer image can come from a private registry and will fail to pull. This must be covered for the feature to be complete per the request, but it is lower volume than Story 1.

**Independent Test**: Configure a trusted private registry holding the transformer image, deploy an inference model that triggers transformer chaining, and confirm the transformer pod reaches a running state without manual secret/service-account work.

**Acceptance Scenarios**:

1. **Given** the configured transformer image's host matches a trusted private registry with valid credentials, **When** an inference deployment chains a transformer, **Then** the generated inference workload references a pull secret valid for the transformer image's registry and the transformer pod pulls successfully.
2. **Given** an inference deployment that does **not** chain a transformer (predictor-only), **When** it is deployed, **Then** behavior is unchanged from today (no transformer-related pull secret is required or injected).

---

### User Story 3 - Images from public or unconfigured registries continue to work unchanged (Priority: P3)

A user deploys a workload whose image is in a public registry, or in a registry for which no credentials are configured. The system does not inject any pull secret for that image, and the deployment behaves exactly as it does today.

**Why this priority**: This is a guardrail/regression-safety story rather than new value. It ensures the new behavior is additive and never breaks existing public-image or no-auth deployments. Lower priority because it preserves current behavior rather than adding capability.

**Independent Test**: Deploy an image from a public registry (no configured credentials) and confirm the generated workload contains no auto-injected pull secret and starts as before.

**Acceptance Scenarios**:

1. **Given** a deployment image whose registry host matches no configured registry (main or trusted), **When** it is deployed, **Then** no pull secret is auto-created or referenced for that image and the deployment proceeds as it does today.
2. **Given** a configured registry that matches the image host but is configured with no-credentials/anonymous access, **When** the workload is deployed, **Then** no credential-bearing pull secret is injected for that registry.

---

### Edge Cases

- **Registry host normalization**: Docker Hub references appear under several aliases (`docker.io`, `index.docker.io`, `registry-1.docker.io`); a configured Docker Hub credential must match a user image expressed under any of these aliases, and vice-versa.
- **Main registry as source**: If the user's image lives in the system's primary configured registry (the one used for builds) and that registry has credentials, the same auto-provisioning applies — not only the explicitly "trusted private" list.
- **Multiple in-scope images on one workload**: An inference workload can carry more than one container image from different registries (e.g., a transformer image plus another container). Each in-scope image's registry must be covered by the referenced credentials.
- **Credential change after deploy**: An administrator rotates the password for a trusted registry. The next deploy/redeploy of an affected workload must result in the workload using the updated credentials (stale secrets must not silently win).
- **Concurrent deploys into the same namespace**: Two deployments that need the same registry credentials deploy at the same time; pull-secret provisioning must be safe under concurrency (no duplicate-creation failures, no race that leaves a workload referencing a missing secret).
- **Undeploy / cleanup**: Removing a deployment must not leave orphaned credential secrets that accumulate indefinitely, nor remove a secret still in use by another live workload.
- **Untrusted private image**: A user deploys a private image from a registry with no configured credentials; the system cannot fabricate credentials, so the pull will fail at the Kubernetes level exactly as today — the system must not pretend success, and the failure should be diagnosable.
- **NIM deployments**: NIM workloads already obtain their pull secret through the existing NGC mechanism; the new auto-provisioning must not conflict with or duplicate that mechanism.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When deploying any image-based deployment (MCP, interceptor, adapter, application), the system MUST determine the registry host of the deployment's image and check whether credentials for that host are available among the administrator-configured registries (the primary registry and the trusted private registries).
- **FR-002**: When deploying an inference service that chains a transformer, the system MUST apply the same determination to the transformer container image (and to any other in-scope container image the system places into the inference workload).
- **FR-003**: When credentials for an in-scope image's registry are available, the system MUST ensure a Kubernetes image-pull secret carrying those credentials exists in the deployment's target namespace before the workload is applied.
- **FR-004**: When such a pull secret has been ensured, the system MUST reference it from the generated workload manifest so that the workload's pods pull the image using those credentials, without requiring an administrator to edit any service account or create any secret by hand.
- **FR-005**: The system MUST treat registries configured with anonymous/no-credentials access as not providing credentials, and MUST NOT inject a credential-bearing pull secret for images served from them.
- **FR-006**: When no configured registry matches the image's host, the system MUST NOT inject any auto-provisioned pull secret for that image, and the deployment MUST proceed with behavior identical to today's.
- **FR-007**: Registry-host matching MUST normalize well-known Docker Hub aliases so that a Docker Hub credential matches images expressed under any of its equivalent hostnames.
- **FR-008**: Pull-secret provisioning MUST be idempotent: deploying, undeploying and re-deploying, duplicating, or changing the image of a deployment MUST NOT fail due to a secret already existing, and MUST result in the workload referencing credentials valid for its current image.
- **FR-009**: When an administrator changes the credentials of a configured registry, a subsequent deploy or redeploy of an affected workload MUST result in the workload using the updated credentials rather than stale ones.
- **FR-010**: Pull-secret provisioning MUST be safe under concurrent deploys into the same namespace, never producing a duplicate-creation error and never leaving a workload referencing a non-existent secret.
- **FR-011**: Auto-provisioned pull secrets MUST be subject to the system's resource-lifecycle handling so that they do not accumulate indefinitely, while never removing credentials still required by a live workload.
- **FR-012**: The new behavior MUST NOT alter how NIM deployments obtain their existing registry pull secret, and MUST NOT inject a conflicting or duplicate pull secret for NIM workloads.
- **FR-013**: The new behavior MUST NOT change the build/copy pipeline's existing use of registry credentials; auto-provisioning is exclusively a deploy-time concern.
- **FR-014**: When an in-scope image is private and its registry is unconfigured (no credentials available), the system MUST NOT report the deploy as succeeded on the basis of credential provisioning; the resulting pull failure MUST be observable through the existing deployment status/error surfaces.
- **FR-015**: The auto-provisioning behavior MUST apply uniformly across all configuration of the supported deployment types without requiring any new per-deployment user input — administrators configure trusted registries once and nothing else is required of the user.

### Key Entities *(include if feature involves data)*

- **Configured Registry**: An administrator-configured registry the system knows credentials for — either the primary build/deploy registry or an entry in the trusted private registries list. Attributes relevant here: registry host, authentication scheme (anonymous vs. credentialed), and credentials when credentialed.
- **Deployment Image Reference**: The fully-qualified image a deployment intends to run, from which a registry host is derived. Exists for image-based deployments and for the transformer container of chained inference deployments.
- **Auto-Provisioned Pull Secret**: A namespace-scoped Kubernetes secret of the image-pull type holding credentials only for the configured registry/registries that serve the deployment's own in-scope images (narrowed, least-privilege — not the full configured set), created/maintained by the system and referenced from generated workloads. Distinct from the existing build-time docker-config secret (which aggregates all configured registries) and from per-deployment sensitive-env secrets.
- **Generated Workload Manifest**: The CRD the system produces for a deployment (image-based Service, inference InferenceService, etc.) into which the pull-secret reference is injected.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For an image-based deployment whose image is in a configured credentialed registry, deploying it requires **zero** manual administrator actions on Kubernetes secrets or service accounts (down from the current manual two-step process), and the workload reaches a running state.
- **SC-002**: An inference deployment whose transformer image is in a configured credentialed registry reaches a running transformer pod with zero manual administrator secret/service-account actions.
- **SC-003**: 100% of deployments whose images are in public or unconfigured registries continue to deploy with no auto-injected pull secret and no change in observed behavior compared to the prior release.
- **SC-004**: After rotating a configured registry's credentials, the next redeploy of an affected workload pulls successfully using the new credentials in 100% of cases, with no manual secret edits.
- **SC-005**: Repeated deploy → undeploy → redeploy cycles of the same deployment succeed every time with no "secret already exists" or "secret not found" failures.
- **SC-006**: Administrators no longer need documented manual steps for "create a docker pull secret and patch the service account" for trusted-registry images; the corresponding operational guidance is reduced to "configure trusted registries once."

## Assumptions

- **Attachment mechanism**: Credentials are attached by referencing an image-pull secret directly from the generated workload manifest (per the user's "add this secret to the CRD"), rather than by mutating a shared/default Kubernetes service account. This keeps provisioning per-workload and avoids cluster-wide side effects.
- **Match-to-provision policy**: A pull secret is provisioned only when the in-scope image's registry host matches a configured registry that carries credentials. Unmatched or anonymous registries yield no injected secret (Story 3 / FR-005, FR-006).
- **Inference predictor model source**: The inference predictor's model is sourced as a model artifact (HuggingFace), not as a user-supplied container image from a private registry, so the in-scope inference target for image pulls is the transformer container (and any additional container images the system injects), not the predictor's model artifact. The serving-runtime image for the predictor is cluster/runtime-managed and out of scope.
- **NIM out of scope for change**: NIM deployments retain their existing NGC pull-secret mechanism unchanged; this feature does not extend to or modify NIM credential handling beyond ensuring no conflict.
- **Build pipeline unchanged**: The existing aggregation of registry credentials for build/copy jobs is unaffected; this feature adds a parallel deploy-time path.
- **Credential source of truth**: The set of registries and their credentials comes entirely from the existing administrator configuration (primary registry + trusted private registries). No new user-facing configuration is introduced for the common case.
- **Namespace**: Deployments target a known namespace per the existing deployment model; pull secrets are provisioned in that same namespace.
- **Secret lifecycle**: Auto-provisioned pull secrets are managed by the system and tracked by the existing disposable-resource lifecycle so they are reconciled/cleaned up rather than left to accumulate, subject to not removing secrets in active use.
