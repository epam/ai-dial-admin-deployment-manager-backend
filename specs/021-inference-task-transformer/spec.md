# Feature Specification: Auto-Detected HuggingFace Inference Tasks with Chained Transformers

**Feature Branch**: `021-inference-task-transformer`
**Created**: 2026-05-20
**Status**: Implemented
**Capability**: inference-deployments, kubernetes-manifests
**Input**: User description: "I need to add flag to HF model deployment, should be something like `task: text_classification` - tho use the best logic for naming. When task is set and equal to `text_classification` `id2label` mapping is required too. Under the hood, application need to create InferenceService with transformer. Not only predictor. Transformer image should be set using env var. As example look to C:\Users\Oleksii_Donets\IdeaProjects\kserve-text-classification-transformer"

## Clarifications

### Session 2026-05-20

- Q: Which fields should be auto-detected from HuggingFace model metadata at create/update time? → A: Both `task` and `id2label`. The system reads HF Hub metadata for the deployment's model and computes both. There are no operator-facing `task` / `id2label` input fields.
- Q: If a sequence-classification model's config.json has no usable id2label, what should the system do? → A: Reject the operation with an actionable error. No fallback to stub labels, predictor-only, or numeric labels — every other choice produces silent-mislabel or contract-violating output.
- Q: Should operators be able to override the auto-detected id2label? → A: No override. Auto-detection is the only path. Operators who need different labels (translation, renaming) must fork the model with a custom `config.json`.
- Q: Where should the transformer container's resource requests/limits be defined? → A: Application properties, exposed as env vars at manager startup. Same values apply to every chained deployment in a cluster. No per-deployment knob.

### Session 2026-05-20 (revision 2)

- Q: Persist detected `task` and `id2label` on the deployment row, or fetch them at deploy time? → A: **Fetch at deploy time.** No new columns, no migrations, no API fields. Detection runs inside manifest generation. Trade-off: HF Hub must be reachable at deploy time, and contract-violation errors (forbidden predictor args) surface at deploy rather than create. The K8s mutation is still gated, so the "no silent runtime failure" property is preserved.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Deploy a HuggingFace text-classification model with zero ceremony (Priority: P1)

An operator picks a fine-tuned sequence-classification model on the HuggingFace Hub (e.g. `distilbert-base-uncased-finetuned-sst-2-english`) and creates an inference deployment with just the model name and the usual inference-deployment fields — no task flag, no label map. On deploy, the system reads the model's metadata from HF Hub, recognizes it as a text-classification model, reads the model's own `id2label` from its `config.json`, and emits a KServe `InferenceService` that chains the predictor with a text-classification transformer. The public endpoint returns HuggingFace-API-shaped responses with the model's own human-readable labels.

**Why this priority**: This is the entire UX. The operator's mental model is "I picked a text-classification model; I want text-classification output." Any operator-facing task / id2label field is friction that risks the silent-mislabel failure mode if they get it wrong. Auto-detection eliminates that class of error.

**Independent Test**: Create an inference deployment with `source.modelName = "distilbert-base-uncased-finetuned-sst-2-english"` and nothing else task-related. Deploy. Inspect the manifest: both `predictor` and `transformer` blocks present, the transformer container's `ID2LABEL` env var equals the detected map serialized. Call the resulting endpoint with a HuggingFace-style body, get back `[{label, score}, ...]` with the detected labels.

**Acceptance Scenarios**:

1. **Given** a create request with a HuggingFace source whose model has `pipeline_tag = "text-classification"`, **When** the deployment is created, **Then** 201 is returned. (No detection runs at create — detection is deferred to deploy.)
2. **Given** that deployment, **When** it is deployed, **Then** the system fetches the model's HuggingFace metadata, recognises the model as text-classification, and the generated KServe `InferenceService` manifest contains both a `predictor` block and a `transformer` block; the transformer container's `ID2LABEL` env var is the detected map serialized as a JSON object with stringified-integer keys.
3. **Given** that deployment, **When** the manifest is generated, **Then** the predictor's container args include `--return_raw_logits` and `--task=sequence_classification` (auto-injected); the predictor block carries `protocolVersion: v2`.
4. **Given** that deployment in the `RUNNING` state, **When** the deployment's URL is read, **Then** the URL resolves to the transformer component, not the predictor.

---

### User Story 2 - Non-text-classification models continue to deploy as predictor-only (Priority: P1)

An operator deploys a model that is not a sequence-classification model — a translation model, an embedding model, a generative LLM, or any other HuggingFace task. Detection runs at deploy time, returns `NONE`, and the manifest is predictor-only — identical in shape to pre-feature deployments. This preserves backward compatibility for every existing non-classification inference deployment.

**Why this priority**: Without this guarantee, every existing inference deployment for a non-classification model could break on its next deploy. The detection rule must cleanly distinguish text-classification from everything else.

**Independent Test**: Create an inference deployment with a non-classification model (e.g. a translation model). Deploy; confirm the manifest has no `transformer` block.

**Acceptance Scenarios**:

1. **Given** a create request with a HuggingFace model whose `pipeline_tag` is not `text-classification` (e.g. `translation`, `summarization`, `text-generation`), **When** the deployment is created and deployed, **Then** detection at deploy returns `NONE`, the request is accepted, and the manifest is predictor-only.
2. **Given** such a deployment, **When** deployed, **Then** the generated `InferenceService` manifest has only a `predictor` block — no `transformer`, no auto-injected predictor args, no protocol pinning beyond the existing defaults.
3. **Given** an existing inference deployment (created before this feature), **When** it is redeployed, **Then** detection runs at deploy — no migration, no schema change required. The result is computed fresh from HF on every deploy.

---

### User Story 3 - Refuse to deploy when model metadata is unusable (Priority: P1)

An operator picks a model that looks like a text-classification model but whose `config.json` lacks a usable `id2label` (missing entirely, or populated with HF's auto-generated `LABEL_0` / `LABEL_1` stubs); or picks a model that doesn't exist on the Hub; or HF Hub is unreachable from the manager. The system refuses **at deploy time** with a clear, actionable error before any cluster mutation. No deployment with usable-looking topology but unusable labels ever reaches the cluster.

**Why this priority**: The silent-mislabeling failure mode is the worst possible outcome — clients get plausible-looking responses with wrong labels. Catching it before any K8s mutation, with a message that names the problem, is the only acceptable behavior.

**Independent Test**: Submit deploy requests for the failure cases below and confirm each returns the right status code with an actionable message and no `InferenceService` is created.

**Acceptance Scenarios**:

1. **Given** a deploy request for a sequence-classification model whose `config.json` is missing the `id2label` field, **When** submitted, **Then** the deploy fails with 400 naming the model and the missing field; no `InferenceService` is created.
2. **Given** a deploy request for a sequence-classification model whose `config.json` has only HF's stub labels (e.g. all values matching `^LABEL_\d+$`), **When** submitted, **Then** the deploy fails with 400 explaining that stub labels are not usable.
3. **Given** a deploy request whose `modelName` does not resolve on the Hub (model not found / private without credentials), **When** submitted, **Then** the deploy fails with 400 with the HF Hub error surfaced.
4. **Given** the HF Hub is unreachable from the manager (network failure, DNS failure, rate-limited), **When** a deploy request is submitted, **Then** the deploy fails with 5xx and an error message indicating the request is retryable. No `InferenceService` is created.

---

### User Story 4 - Operator-controlled transformer image and resources via configuration (Priority: P1)

A platform operator points the manager at a specific build of the text-classification transformer image, and tunes the transformer container's CPU/memory per cluster — all via configuration, no code changes. If the image env var is unset and a chained deployment tries to deploy, the deploy fails with a clear, actionable error rather than a half-deployed cluster mutation.

**Why this priority**: The transformer image is operator-supplied infrastructure (internal registry mirror, pinned version, patched build); the dev-only default cannot leak into production. Resource defaults must be tunable across clusters with different capacity.

**Independent Test**: Set `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE` to a known value, deploy a chained inference deployment, confirm the manifest's transformer container uses that image. Override one of the resource env vars, redeploy, confirm the new value appears in the manifest. Unset the image var, attempt to deploy → confirm a clear error before any cluster mutation.

**Acceptance Scenarios**:

1. **Given** `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE` set, **When** a chained deployment is deployed, **Then** the manifest's transformer container's `image` field equals that value.
2. **Given** `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE` unset/blank, **When** a chained deployment is deployed, **Then** the deploy fails with an error naming the missing property; no `InferenceService` is created.
3. **Given** the resource env vars set (any subset of `..._CPU_REQUEST`, `..._CPU_LIMIT`, `..._MEMORY_REQUEST`, `..._MEMORY_LIMIT`), **When** a chained manifest is generated, **Then** those values appear on the transformer container's resources block; unset values use the documented defaults (`100m` / `500m` / `256Mi` / `512Mi`).

---

### User Story 5 - Updating the deployment's model picks up the new task on next deploy (Priority: P2)

An operator updates the `modelName` on an existing inference deployment — to a newer fine-tune, a different model entirely, or a different task category. On the next deploy, the system re-runs detection against the new model and emits the appropriate manifest topology (chained → predictor-only, or vice versa, or chained with a different label set).

**Why this priority**: Operators iterate. Without this, a model swap silently keeps the old labels, which is the same silent-mislabel failure mode the feature exists to eliminate. Because detection runs on every deploy, no special update-time handling is required.

**Independent Test**: Create and deploy a chained deployment for a text-classification model. Update its `modelName` to a translation model. Redeploy; confirm the manifest now has no transformer. Reverse the flow.

**Acceptance Scenarios**:

1. **Given** a chained deployment, **When** the model is updated to a non-classification model and the deployment is redeployed, **Then** detection returns `NONE` and the new manifest has no transformer.
2. **Given** a predictor-only deployment, **When** the model is updated to a text-classification model with valid metadata and the deployment is redeployed, **Then** detection returns `TEXT_CLASSIFICATION` and the new manifest adds the transformer.
3. **Given** an update that changes the modelName to a model whose metadata is unusable (per Story 3), **When** the deployment is redeployed, **Then** the deploy is rejected (400 or 5xx as appropriate); the running pod is unaffected.

---

### Edge Cases

- **Model has `pipeline_tag` set to something benign but its `architectures` are `*ForSequenceClassification`** → detected as `TEXT_CLASSIFICATION`. The two signals are ORed; architectures is the fallback when the model card forgets to set the tag.
- **Model has `pipeline_tag = "text-classification"` but no `id2label` in config.json** → reject the deploy with 400 (Story 3).
- **Model's id2label contains only `LABEL_0`, `LABEL_1`, …** → reject (Story 3). Stub labels produce useless output indistinguishable from numeric indices; better to refuse than to deploy.
- **Operator supplies their own `command` or `args` for the predictor on a chained deployment** → the auto-injected flags (`--return_raw_logits`, `--task=sequence_classification`) are still always injected. The operator-supplied args are merged on top. If they contain a flag from the forbidden list (FR-014a) — most notably `--return_probabilities` or `--task=<other>` — the deploy is rejected with 400 naming the offending arg before any cluster mutation. There is no "user wins" path for the chained contract.
- **Operator supplies compatible args (e.g. `--dtype=float16`)** → passes through unchanged; appears in the manifest alongside the auto-injected flags.
- **HF Hub temporarily unreachable at deploy** → 5xx with retryable error message. No `InferenceService` is created. The operator retries.
- **Model retrained on HF with new labels** → because detection runs fresh on every deploy, the next deploy picks up the new labels. No staleness, no manual refresh endpoint required.
- **modelFormat is not `huggingface`** → detection is skipped; predictor-only manifest. Detection is only meaningful for HuggingFace-sourced inference deployments.
- **Endpoint URL resolution**: chained deployment → transformer component's URL; predictor-only → unchanged. Status `RUNNING` requires both components healthy when chained; status of a predictor-only deployment is unchanged.

## Requirements *(mandatory)*

### Functional Requirements

#### Domain & detection

- **FR-001**: Detection of `task` and `id2label` is computed at **deploy time** from the HuggingFace model's metadata. No persisted columns on the `inference_deployment` table; no fields on request/response DTOs.
- **FR-002**: The REST API SHALL NOT expose operator-input fields for `task` or `id2label`. Operators cannot set, override, or clear either.
- **FR-003**: On deploy, the system SHALL fetch the model's HuggingFace metadata via the existing `huggingface` capability and atomically compute `task` and `id2Label`. Both flow inline into manifest generation and are not stored anywhere except the resulting KServe `InferenceService` spec.
- **FR-004**: Detection rule: `task = TEXT_CLASSIFICATION` if and only if **either** the HF Hub API response carries `pipeline_tag = "text-classification"` **or** the model's `config.json` `architectures` array contains an entry matching the pattern `.*ForSequenceClassification`. Otherwise `task = NONE`.
- **FR-005**: When `task = TEXT_CLASSIFICATION`, `id2Label` SHALL be derived from the model's `config.json` `id2label`. The derived map MUST satisfy:
  - keys are parseable as non-negative integers,
  - the key set is dense — equal to `{0, 1, …, n-1}` for some `n ≥ 1`,
  - every value is a non-empty string,
  - no value matches the HF auto-stub pattern `^LABEL_\d+$`.
- **FR-006**: Any failure in FR-005 SHALL reject the deploy with HTTP 400 naming the model and the specific failed condition. No cluster mutation occurs.
- **FR-007**: If the HF Hub API call required by FR-003 fails (model not found, unauthorized, network failure, rate-limited), the deploy SHALL be rejected:
  - Model-not-found / unauthorized → 400 with the upstream error surfaced.
  - Transient infra failure (timeout, 5xx, network unreachable) → 5xx with a retryable error message.
  No cluster mutation occurs in either case.

#### Manifest generation — chained predictor + transformer

- **FR-010**: When `task = TEXT_CLASSIFICATION`, the generated KServe `InferenceService` manifest SHALL contain both a `predictor` block (the existing HuggingFace model server configuration) and a `transformer` block (the text-classification adapter). When `task = NONE`, only the `predictor` block is emitted — identical to the pre-feature manifest shape.
- **FR-011**: The transformer container's image SHALL be sourced from the configured property (FR-015). The container name SHALL be `kserve-container` (KServe convention).
- **FR-012**: The transformer container's environment SHALL include `ID2LABEL`, whose value is `id2Label` serialized as a JSON object with stringified-integer keys and string values (e.g. `{"0":"NEGATIVE","1":"POSITIVE"}`) — the exact format the transformer parses on startup.
- **FR-013**: The transformer container's args SHALL include `--model_name=<deploymentName>` and `--predictor_protocol=v2`. The predictor block SHALL be emitted with `protocolVersion: v2`.
- **FR-014**: For deployments detected as `TEXT_CLASSIFICATION`, the predictor's container args SHALL **always** include `--return_raw_logits` and `--task=sequence_classification` (auto-injected by the system). Operator-supplied `command`/`args` are merged on top — they do not replace these.
- **FR-014a**: For deployments detected as `TEXT_CLASSIFICATION`, manifest generation SHALL reject (HTTP 400) operator-supplied `command` or `args` containing any flag from the forbidden list. The initial list is:
  - `--return_probabilities` — conflicts with `--return_raw_logits`.
  - `--task=<value>` for any `<value>` other than `sequence_classification`.

  Compatible operator-supplied args (e.g. `--dtype=float16`, custom `--model_id`) pass through unchanged. The validation error SHALL name the offending arg, and SHALL be raised before any cluster mutation.

#### Configuration — transformer image and resources

- **FR-015**: The text-classification transformer image SHALL be sourced from a single application property exposed as the environment variable `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE`. The property has no default value.
- **FR-016**: When a chained deploy is requested and the transformer-image property is unset or blank, the deploy operation SHALL fail with HTTP 5xx and an error identifying the missing property. The error MUST be generated before any cluster mutation; no `InferenceService` is created.
- **FR-017**: The transformer container's resource requests and limits SHALL be sourced from application properties, exposed as env vars at manager startup:
  - `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_CPU_REQUEST` (default `100m`)
  - `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_CPU_LIMIT` (default `500m`)
  - `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_MEMORY_REQUEST` (default `256Mi`)
  - `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_MEMORY_LIMIT` (default `512Mi`)

  Values apply uniformly to every chained deployment in the cluster. There is no per-deployment override.

#### Endpoint resolution and status

- **FR-018**: For chained deployments, the resolved public URL of the deployment SHALL be the transformer component's URL. For predictor-only deployments, behavior is unchanged.
- **FR-019**: The deployment status mapping SHALL report `RUNNING` only when both the predictor and the transformer components are healthy. If either component is failed, the status SHALL be `CRASHED`; otherwise `PENDING`. Predictor-only deployments retain the existing single-component status logic. The chained-vs-not distinction is read from the live `InferenceService` (presence of a `transformer` block), not from any persisted field.

#### Cross-cutting

- **FR-022**: All new endpoint behaviors (create, update, read, deploy) SHALL conform to the API conventions documented in `specs/api-conventions/spec.md` — versioning, error response shape, `@LogExecution`, write-role annotations on mutating endpoints.

### Key Entities

- **Detection result**: A transient computation at deploy time: a `task` enum (`TEXT_CLASSIFICATION` | `NONE`) and an optional `id2Label` map (`Map<Integer, String>`). Never persisted. Sourced from the model's HuggingFace metadata. Serialized into the transformer container's `ID2LABEL` env var when chained.
- **Detection inputs**: HF Hub model metadata (`/api/models/{name}`) and model's `config.json` (`/{name}/resolve/main/config.json`). Read via the existing `huggingface` capability.
- **Transformer-image configuration**: A single required application property, env-var-overridable, no default, validated at deploy time when a chained deployment is being deployed.
- **Transformer-resource configuration**: Four application properties (CPU req/limit, memory req/limit), env-var-overridable, with sensible defaults baked in.
- **KServe InferenceService manifest**: Now conditionally bears a `transformer` block alongside the existing `predictor` block, gated on detection.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can create a deployment for any text-classification model on HF Hub by supplying only the model name and standard inference-deployment fields, and the resulting public endpoint returns HF-API-shaped `[{label, score}, ...]` responses with the model's own labels — no manual `task` or `id2label` inputs.
- **SC-002**: All existing inference deployments (created before this feature shipped, including those serving non-classification models) continue to deploy and serve identically — verified by all existing inference functional tests passing without modification. No schema migration is required.
- **SC-003**: Every model whose HF metadata cannot produce a usable `id2Label` (missing field, stub labels, sparse / non-dense keys, empty values) is rejected at deploy time with HTTP 400 and an actionable error. No such deployment reaches the cluster.
- **SC-004**: HF Hub unreachable / transient failures at deploy return HTTP 5xx with a retryable error; no `InferenceService` is created; the operation succeeds idempotently on retry once HF is reachable.
- **SC-005**: The transformer image is fully controlled by configuration — a configuration change followed by a redeploy uses the new image on the next manifest generation, with no code changes. When the property is unset, chained deploys fail synchronously with an actionable error.
- **SC-006**: Transformer container resources can be tuned per cluster via four env vars; defaults are sensible for SST-2-class workloads (~440 MB BERT-base derivatives).
- **SC-007**: A chained deployment is `RUNNING` only when both predictor and transformer are healthy; either component's failure surfaces as `CRASHED` or `PENDING` per the existing taxonomy.
- **SC-008**: A `modelName` change on an existing deployment correctly triggers re-detection at the next deploy; topology transitions (chained → predictor-only or vice versa) reflect on next deploy without any update-time bookkeeping.
- **SC-010**: No forbidden operator-supplied predictor arg (per FR-014a) ever reaches the cluster — every such case is rejected before any cluster mutation, with HTTP 400 naming the offending arg.

## Assumptions

- **HF Hub reachability at deploy time**: The manager has network egress to `huggingface.co` from the deploy code path (consistent with the existing `huggingface` capability used by the model-search feature). Operators with air-gapped clusters either run an HF mirror or accept that chained-mode deploys require connectivity. This is the explicit trade-off accepted in revision 2: simpler code in exchange for HF reachability at deploy.
- **Reference transformer contract**: The implementation contract — env vars, args, protocol — matches the existing `hf-text-classification-transformer` repository at `C:\Users\Oleksii_Donets\IdeaProjects\kserve-text-classification-transformer`. Operators substituting a different image are responsible for matching that contract.
- **Predictor is `huggingfaceserver`**: Chained mode is only emitted for inference deployments whose `modelFormat = "huggingface"` and whose `source.$type = "huggingface"`. Other model formats / source types stay predictor-only regardless of HF metadata.
- **Protocol is KServe v2**: Both predictor and transformer use KServe's Open Inference Protocol v2. The predictor block is emitted with `protocolVersion: v2` for chained deployments.
- **HF taxonomy bridging**: The HF Inference API calls this task `text-classification`; the `huggingfaceserver` predictor uses `sequence_classification` (HuggingFace's `AutoModelForSequenceClassification` taxonomy). The system bridges these names internally — `TEXT_CLASSIFICATION` is recognized from either signal (`pipeline_tag` or `architectures`), and the predictor arg is always `--task=sequence_classification`.
- **Stub-label rejection**: `id2label` values matching `^LABEL_\d+$` (HuggingFace's `AutoConfig` default) are treated as not-usable. This deliberately rejects models whose owners never customized their labels; the alternative — passing through `LABEL_0`/`LABEL_1` to clients — defeats the entire purpose of the feature.
- **No schema migration**: This feature ships zero Flyway changes. No new columns, no audit-table changes. Existing inference deployments work without modification.
- **Transformer-scaling co-location**: KServe scales the chained pair (predictor + transformer) together. The existing `scaling` field on the deployment maps to predictor-level scaling; transformer-side scaling knobs are not exposed (the transformer is stateless and the chained pair shares pod lifecycle in practice).
- **`DEFAULT_FUNCTION_TO_APPLY` not exposed**: The transformer's per-request `function_to_apply` parameter remains available to clients; the deployment-level default (`softmax`, the transformer's own default) is not surfaced as a deployment field in v1. Add when an operator with a multi-label model asks for it.
- **MCP endpoint surface**: The reference transformer exposes an MCP tool at `/mcp` on the same port as the REST endpoint. Surfacing this through the `mcp-servers` capability is out of scope for this feature; the endpoint is reachable directly via the deployment's resolved URL.
- **Detection is non-cached at the manager**: Each deploy reads HF live. No in-process cache, no persisted snapshot. If model owners change labels upstream after deployment, the next deploy picks up the new labels automatically.
