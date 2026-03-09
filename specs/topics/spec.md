# Topics

## Purpose
This spec describes the topics listing endpoint — a read-only endpoint that returns all topic labels currently in use across image definitions. Topics are not a fixed enumeration; they are derived dynamically from the topics attached to image definitions in the database.

Status: **Implemented**

## Key Terms
- **Topic**: A free-form string label used to categorize image definitions (e.g., `"nlp"`, `"vision"`). Topics have no schema of their own — they are created implicitly when assigned to an image definition.
- **Topics enumeration**: The distinct sorted set of all topic values currently referenced by at least one image definition. Returned by `GET /api/v1/topics`.

## Requirements

### Requirement: List all available topics
The system SHALL return the complete distinct sorted list of topic values currently in use across all image definitions.

Status: **Implemented**

#### Scenario: Topics available
- **WHEN** `GET /api/v1/topics` is called
- **THEN** a sorted `List<String>` of all distinct topic values currently referenced by at least one image definition is returned with HTTP 200

#### Scenario: No topics in use
- **WHEN** `GET /api/v1/topics` is called and no image definitions have topics assigned
- **THEN** an empty array is returned (not an error)

### Requirement: Topics on image definitions are validated free-form strings
When a topic is assigned to an image definition, each topic value SHALL pass format validation: non-blank, ≤255 characters, no leading or trailing whitespace.

Status: **Implemented**

#### Scenario: Valid topic accepted
- **WHEN** an image definition is created or updated with topics that are non-blank, ≤255 chars, no whitespace padding
- **THEN** the topics are persisted

#### Scenario: Invalid topic rejected
- **WHEN** an image definition is submitted with a blank topic, a topic exceeding 255 chars, or a topic with leading/trailing whitespace
- **THEN** the system responds with 400

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.TopicController`
- Service: `com.epam.aidial.deployment.manager.service.TopicService`
- Repository: `com.epam.aidial.deployment.manager.dao.repository.TopicRepository` (query: distinct topics from ImageDefinitionEntity, ordered alphabetically)
- Response: `List<String>`
- Topic validation: `@ValidTopics` constraint on `ImageDefinitionRequestDto.topics` (non-blank, ≤255 chars, no leading/trailing whitespace)
- Read-only — no create, update, or delete operations on topics via this endpoint
