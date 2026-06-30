# Feature Specification: Model Serving Capability API

**Feature Branch**: `024-model-serving-capability`  
**Created**: 2026-06-29  
**Status**: Implemented  
**Capability**: inference-deployments  
**Input**: User description: "Expose an API about what serving an inference model supports. Using the existing inference-task detection, additionally recognise text-generation models. Based on the detected task, tell the frontend what the model exposes: text-generation → chat completion endpoint, text-classification → mcp toolset."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Frontend learns what a deployed model exposes (Priority: P1)

A user opens an inference deployment in the admin UI. The frontend needs to show the user how the model can be consumed — as a chat-completion model or as an MCP toolset — and route or label the deployment accordingly. The frontend reads a single capability value from the deployment and decides what to display.

**Why this priority**: This is the whole point of the feature. Without the capability value on the deployment, the frontend cannot tell a chat model from a classification/toolset model, and cannot present the correct consumption surface to the user.

**Independent Test**: Create an inference deployment from a known text-generation HuggingFace model, fetch the deployment via the API, and confirm the response carries the capability value that maps to chat completion. Repeat with a known text-classification model and confirm it carries the value that maps to an MCP toolset.

**Acceptance Scenarios**:

1. **Given** an inference deployment created from a text-generation model, **When** the frontend fetches that deployment, **Then** the response includes a capability value of `TEXT_GENERATION`.
2. **Given** an inference deployment created from a text-classification (sequence-classification) model, **When** the frontend fetches that deployment, **Then** the response includes a capability value of `TEXT_CLASSIFICATION`.
3. **Given** an inference deployment created from a model that matches neither task, **When** the frontend fetches that deployment, **Then** the response includes a capability value of `NONE`.
4. **Given** a list of inference deployments, **When** the frontend fetches the list, **Then** each inference deployment in the list carries its own capability value.

---

### User Story 2 - Text-generation models are recognised by detection (Priority: P1)

The system's inference-task detection must classify a HuggingFace model as text-generation when the model's metadata indicates it. Today detection only distinguishes text-classification from "none"; text-generation models fall into "none" and the frontend cannot tell they are chat models.

**Why this priority**: User Story 1 cannot deliver the chat-completion case without this. It is the new detection input that the capability value depends on.

**Independent Test**: Run detection against a known text-generation model and confirm the result is the text-generation task; run it against a known text-classification model and a known unrelated model and confirm those results are unchanged from today.

**Acceptance Scenarios**:

1. **Given** a HuggingFace model whose metadata identifies it as text-generation, **When** detection runs, **Then** the detected task is `TEXT_GENERATION`.
2. **Given** a HuggingFace model that is sequence-classification, **When** detection runs, **Then** the detected task is still `TEXT_CLASSIFICATION` (unchanged).
3. **Given** a HuggingFace model that is neither, **When** detection runs, **Then** the detected task is `NONE` (unchanged).

---

### User Story 3 - Capability stays correct when the model source changes (Priority: P2)

When a user changes the model source of an inference deployment to a different HuggingFace model, the stored capability must be re-evaluated so the frontend never shows a stale value.

**Why this priority**: Keeps the persisted value trustworthy over a deployment's lifetime. Lower than P1 because the initial create path already delivers core value.

**Independent Test**: Create a deployment from a text-classification model, confirm the capability, then update its source to a text-generation model and confirm the capability value updates to `TEXT_GENERATION`.

**Acceptance Scenarios**:

1. **Given** an existing inference deployment with capability `TEXT_CLASSIFICATION`, **When** its model source is changed to a text-generation model, **Then** the persisted capability becomes `TEXT_GENERATION`.

### Edge Cases

- **HuggingFace Hub unreachable at create/update time**: The deployment create/update flow already calls detection today and fails the operation when the Hub is unreachable or the model metadata is missing/unusable. This feature does not change that behaviour — capability is only persisted when detection succeeds.
- **Existing deployments created before this feature**: Deployments that predate the stored capability field have no recorded value. The response MUST still be well-formed; see FR-007 for how an absent value is represented.
- **A model that legitimately matches both signals** (e.g. metadata suggests classification and generation): detection MUST resolve to a single deterministic capability value (see FR-002a). Ambiguity MUST NOT produce two values.
- **Non-inference deployments** (MCP, interceptor, adapter, application, NIM): out of scope — they do not carry this capability value.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST detect a HuggingFace model as text-generation based on its HuggingFace metadata, in addition to the existing text-classification and "none" outcomes.
- **FR-002**: Inference-task detection MUST produce exactly one of three task values for any model: text-generation, text-classification, or none.
- **FR-002a**: When a model's metadata satisfies more than one task signal, detection MUST apply a fixed, documented precedence so the outcome is deterministic. (Recommended precedence to confirm at plan time: text-classification before text-generation, since sequence-classification architectures are the more specific signal.)
- **FR-003**: The system MUST persist the detected task on the inference deployment at the time the deployment is created.
- **FR-004**: The system MUST re-evaluate and update the persisted task whenever the deployment's model source changes.
- **FR-005**: The system MUST expose the persisted task as a capability value on the existing inference deployment API response (both single-deployment fetch and list responses).
- **FR-006**: The capability value MUST be a stable, enumerated type — one of `TEXT_GENERATION`, `TEXT_CLASSIFICATION`, `NONE` — and MUST NOT include endpoint URLs or paths; the frontend owns the mapping from capability to consumption surface (text-generation → chat completion, text-classification → MCP toolset).
- **FR-007**: For inference deployments that have no persisted capability (created before this feature, or never deployed), the API response MUST represent the value as `NONE` or as an explicit "unknown/absent" value — chosen at plan time and applied consistently — so the frontend can handle it without error.
- **FR-008**: The capability value MUST be read-only over the API — clients cannot set or override it on create/update; it is system-computed only.
- **FR-009**: Adding text-generation detection MUST NOT change the existing manifest-generation behaviour for text-classification and none (the transformer chaining contract in `specs/021-inference-task-transformer/spec.md` is preserved).

### Key Entities *(include if feature involves data)*

- **Inference task / serving capability**: A system-computed classification of a HuggingFace-sourced inference model. Values: text-generation, text-classification, none. Computed from HuggingFace model metadata; stored on the inference deployment; exposed read-only to clients.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For any inference deployment, the frontend can determine the model's consumption surface (chat completion, MCP toolset, or none) from a single field in the deployment response, with no extra API call.
- **SC-002**: 100% of newly created inference deployments backed by a recognisable text-generation model report the text-generation capability.
- **SC-003**: Existing text-classification and none outcomes are unchanged for 100% of models that were correctly classified before this feature (no regressions in detection or manifest generation).
- **SC-004**: When a deployment's model source changes, the reported capability matches the new model on the next fetch (no stale values).

## Assumptions

- Scope is HuggingFace-sourced **inference deployments only**. NIM and image-based deployment types (MCP, interceptor, adapter, application) are out of scope and do not carry this value.
- The capability is **persisted at create/deploy time and read back** (not recomputed live on every read), so the read path needs no HuggingFace Hub call and stays fast and stable.
- The frontend owns the human-facing mapping and routing; the backend returns only the enumerated capability type.
- Detection of text-generation reuses the existing HuggingFace metadata already fetched today (model `pipeline_tag` and/or `config.json` architectures); no new external integration is introduced.
- The existing failure behaviour of detection at create/update time (model not found, metadata missing/unusable, Hub unreachable) is unchanged by this feature.
