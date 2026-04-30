# Feature Specification: Deployment Topics

**Feature Branch**: `001-deployment-topics`
**Created**: 2026-03-05
**Status**: Draft
**Capability**: topics
**Input**: User description: "It should be possible to add topics on deployments, similarly to image definitions"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Assign Topics When Creating a Deployment (Priority: P1)

As an administrator, I want to assign topic labels to a deployment when I create it, so that deployments can be categorized and discovered by topic.

**Why this priority**: Core capability — without the ability to set topics on creation, no other topic-related functionality is useful.

**Independent Test**: Can be fully tested by creating a deployment with a list of topics and verifying the topics are persisted and returned in the deployment response.

**Acceptance Scenarios**:

1. **Given** a valid deployment creation request with a `topics` field containing `["nlp", "vision"]`, **When** the deployment is created, **Then** the response includes the same topics list and the topics are persisted.
2. **Given** a valid deployment creation request without the `topics` field, **When** the deployment is created, **Then** the deployment is created successfully with an empty topics list (backward-compatible).
3. **Given** a deployment creation request with `topics` set to an empty list, **When** the deployment is created, **Then** the deployment is created with no topics assigned.

---

### User Story 2 - Update Topics on an Existing Deployment (Priority: P1)

As an administrator, I want to update the topic labels on an existing deployment, so that I can recategorize deployments as requirements change.

**Why this priority**: Equal to creation — administrators must be able to modify topics after the fact.

**Independent Test**: Can be fully tested by updating a deployment with new topics and verifying the topics are replaced in the response.

**Acceptance Scenarios**:

1. **Given** an existing deployment with topics `["nlp"]`, **When** the deployment is updated with topics `["nlp", "vision"]`, **Then** the response reflects the updated topics.
2. **Given** an existing deployment with topics `["nlp"]`, **When** the deployment is updated with an empty topics list, **Then** all topics are removed from the deployment.
3. **Given** an existing deployment with topics `["nlp"]`, **When** the deployment is updated without the `topics` field, **Then** the existing topics remain unchanged.

---

### User Story 3 - View Topics on a Deployment (Priority: P1)

As an administrator, I want to see the topics assigned to a deployment when I retrieve deployment details, so that I can understand how each deployment is categorized.

**Why this priority**: Essential for visibility — topics must be readable to be useful.

**Independent Test**: Can be fully tested by retrieving a deployment and verifying the topics field is present in the response.

**Acceptance Scenarios**:

1. **Given** a deployment with topics `["nlp", "vision"]`, **When** the deployment details are retrieved, **Then** the response includes the topics list.
2. **Given** a deployment with no topics, **When** the deployment details are retrieved, **Then** the topics field is present as an empty list.

---

### User Story 4 - Topics Listing Includes Deployment Topics (Priority: P2)

As an administrator, I want the global topics listing endpoint to include topics from both image definitions and deployments, so that I get a complete view of all topics in use across the system.

**Why this priority**: Enhances discoverability but is not essential for the core topic-on-deployment functionality.

**Independent Test**: Can be fully tested by assigning a unique topic to a deployment (not on any image definition) and verifying it appears in the `GET /api/v1/topics` response.

**Acceptance Scenarios**:

1. **Given** a deployment with topic `"robotics"` and no image definitions with that topic, **When** `GET /api/v1/topics` is called, **Then** `"robotics"` appears in the returned list.
2. **Given** both an image definition and a deployment with topic `"nlp"`, **When** `GET /api/v1/topics` is called, **Then** `"nlp"` appears exactly once (deduplicated).

---

### User Story 5 - Duplicate Deployment Preserves Topics (Priority: P2)

As an administrator, I want topics to be copied when I duplicate a deployment, so that the cloned deployment retains the same categorization.

**Why this priority**: Convenience feature that maintains consistency when cloning deployments.

**Independent Test**: Can be fully tested by duplicating a deployment with topics and verifying the duplicate has the same topics.

**Acceptance Scenarios**:

1. **Given** a deployment with topics `["nlp", "vision"]`, **When** the deployment is duplicated, **Then** the new deployment has topics `["nlp", "vision"]`.

---

### User Story 6 - Export/Import Preserves Deployment Topics (Priority: P2)

As an administrator, I want deployment topics to be included in configuration export and correctly restored during import, so that topics are not lost during system migration.

**Why this priority**: Important for operational workflows but secondary to core CRUD.

**Independent Test**: Can be fully tested by exporting a configuration with deployment topics and importing it, then verifying topics are restored.

**Acceptance Scenarios**:

1. **Given** a deployment with topics, **When** the configuration is exported and then imported, **Then** the deployment topics are preserved.

---

### Edge Cases

- What happens when a deployment is created with duplicate topic values (e.g., `["nlp", "nlp"]`)? System should deduplicate silently or reject — following the same behavior as image definitions.
- What happens when a deployment is created with invalid topics (blank, too long, whitespace-padded)? System should reject with 400, using the same validation rules as image definitions.
- What happens when a deployment is deleted? Associated topics should be removed (cascade delete).
- What happens when the same topic exists on both a deployment and an image definition? The topics listing endpoint should return it once (deduplicated).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow an optional list of topics to be provided when creating a deployment.
- **FR-002**: System MUST allow topics to be updated when modifying an existing deployment.
- **FR-003**: System MUST return the topics list in deployment response payloads (detail and list views).
- **FR-004**: System MUST validate deployment topics using the same rules as image definition topics: each topic must be non-blank, at most 255 characters, and have no leading or trailing whitespace.
- **FR-005**: System MUST persist deployment topics in a dedicated join table with cascade delete on the parent deployment.
- **FR-006**: The global topics listing endpoint MUST return topics from both image definitions and deployments, deduplicated and sorted alphabetically.
- **FR-007**: System MUST include deployment topics in configuration export and restore them during import.
- **FR-008**: System MUST copy topics when a deployment is duplicated.
- **FR-009**: The topics field MUST be optional and backward-compatible — existing deployments without topics continue to function normally with an empty topics list.

### Key Entities

- **Deployment**: Existing entity representing a running service instance. Extended with an optional `topics` field (list of string labels).
- **Topic**: A free-form string label (max 255 chars) used to categorize both image definitions and deployments. Not a standalone entity — stored as values in join tables.
- **Deployment Topics (join table)**: Associates a deployment ID with topic name values, mirroring the existing `image_definition_topics` table structure.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Administrators can assign, update, and remove topics on deployments using the existing deployment creation and update workflows.
- **SC-002**: The topics listing endpoint returns a unified, deduplicated set of topics from both image definitions and deployments.
- **SC-003**: All existing deployment operations (create, update, delete, duplicate, export, import) continue to work without regression.
- **SC-004**: Topic validation on deployments enforces the same rules as on image definitions, with consistent error messages.

## Assumptions

- The existing `@ValidTopics` validation annotation and `TopicsValidator` will be reused for deployment topics (same validation rules).
- The `deployment_topics` join table will follow the same schema pattern as `image_definition_topics` (composite PK of deployment_id + topic_name, FK with cascade delete).
- The `TopicRepository` query will be extended to union topics from both `image_definition_topics` and `deployment_topics`.
- Deployments without topics (created before this feature) will have an empty topics list — no backfill migration is needed.
