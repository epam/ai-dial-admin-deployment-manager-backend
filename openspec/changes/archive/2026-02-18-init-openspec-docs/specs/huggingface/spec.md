# HuggingFace

## Purpose
This spec describes the HuggingFace model hub integration — searching for AI models and retrieving model metadata from the HuggingFace Hub API, used as a model source for inference deployments.

Status: **Implemented**

## Key Terms
- **HuggingFace Hub**: The public model repository at huggingface.co hosting pre-trained AI models.
- **Model repository**: A HuggingFace model identified by `{owner}/{model-name}` (e.g., `meta-llama/Llama-3-8B`).
- **Cursor-based pagination**: HuggingFace API uses link-header pagination (next/prev URL) rather than offset-based page numbers. The client passes a full `pageUrl` (not just a cursor token) to advance pages.
- **`ModelDto`**: The model metadata returned by the API: `id`, `sha`, `author`, `createdAt`, `lastModified`, `likes`, `downloads`, `parameters` (nullable Long), `tags`, `libraries`, `languages`, `licenses`, `datasets`.

## ADDED Requirements

### Requirement: Search HuggingFace models
The system SHALL query the HuggingFace API to search for available models matching given criteria. The following query parameters are supported: `search` (text search), `author`, `filter`, `sort` (default: `"downloads"`), `limit` (1–1000, default: 100), and `pageUrl` (cursor for pagination).

Status: **Implemented**

#### Scenario: Model search with filter
- **WHEN** `GET /api/v1/huggingface/models` is called with filter parameters
- **THEN** the system forwards the query to HuggingFace and returns matching model entries as `ModelDto` objects

#### Scenario: Default sort by downloads
- **WHEN** `GET /api/v1/huggingface/models` is called without a `sort` parameter
- **THEN** results are sorted by download count (descending)

#### Scenario: Empty results
- **WHEN** the search yields no matching models
- **THEN** an empty list is returned (not an error)

### Requirement: Cursor-based pagination for model listings
HuggingFace model listings SHALL use cursor-based pagination. Results include `nextPageUrl` and `prevPageUrl` links for navigation.

Status: **Implemented**

#### Scenario: First page
- **WHEN** a model search is made without a `pageUrl`
- **THEN** the first page of results is returned with a `nextPageUrl` if more results exist, and `prevPageUrl: null`

#### Scenario: Subsequent page
- **WHEN** a model search is made with a `pageUrl` from a previous response's `nextPageUrl`
- **THEN** the corresponding page of results is returned with updated `nextPageUrl` and `prevPageUrl`

#### Scenario: Last page
- **WHEN** the last page of results is reached
- **THEN** `nextPageUrl` is null in the response

#### Scenario: Limit validation
- **WHEN** `limit` is set to a value outside 1–1000
- **THEN** the system responds with 400

### Requirement: Retrieve model metadata by ID
The system SHALL retrieve detailed metadata for a specific HuggingFace model by its repository identifier.

Status: **Implemented**

#### Scenario: Existing model
- **WHEN** a valid model repository ID is queried
- **THEN** model metadata (`ModelDto`) is returned: id, sha, author, createdAt, lastModified, likes, downloads, parameters, tags, libraries, languages, licenses, datasets

#### Scenario: Non-existent model
- **WHEN** a model repository ID that does not exist is queried
- **THEN** an appropriate not-found response is returned

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.huggingface.web.controller.HuggingFaceController`
- Base path: `GET /api/v1/huggingface/models`
- Search parameters: `search`, `author`, `filter`, `sort` (default: `"downloads"`), `limit` (1–1000, default: 100), `pageUrl`
- Pagination DTO: `com.epam.aidial.deployment.manager.huggingface.web.dto.ModelsPageResponseDto`
  - Fields: `models` (List\<ModelDto\>), `nextPageUrl` (nullable String), `prevPageUrl` (nullable String)
- Model DTO: `com.epam.aidial.deployment.manager.web.dto.ModelDto`
  - Fields: `id`, `sha`, `author`, `createdAt`, `lastModified`, `likes`, `downloads`, `parameters` (nullable Long), `tags`, `libraries`, `languages`, `licenses`, `datasets`
- Integration package: `com.epam.aidial.deployment.manager.huggingface.*`
- Related spec: `inference-deployments`
