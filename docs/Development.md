# Development Guide

This document provides comprehensive information for developers working on the DIAL Deployment Manager Backend, including API documentation, development commands, architecture details, and configuration.

## Table of Contents

- [REST API](#rest-api)
- [Build and Development Commands](#build-and-development-commands)
- [Testing](#testing)
- [Code Quality](#code-quality)
- [Architecture](#architecture)
- [Development Notes](#development-notes)
- [Configuration for Development](#configuration-for-development)

## REST API

The Deployment Manager Backend exposes REST endpoints under the `/api/v1` prefix.

### Main API Endpoints

- `/api/v1/images/definitions` - Image definition management (create, read, update, delete)
- `/api/v1/images/builds` - Image build operations and status tracking
- `/api/v1/deployments` - Deployment management and lifecycle operations
- `/api/v1/deployments/mcp` - MCP-specific endpoints (tools, resources, prompts)
- `/api/v1/health` - Health check endpoints
- `/api/v1/topics` - Topic management
- `/api/v1/disposable` - Disposable resource management

### Internal Endpoints

- `/api/internal/v1/deployments` - Internal deployment management endpoints

## Build and Development Commands

### Building

```bash
# Build the application
./gradlew build

# Build without tests
./gradlew build -x test

# Build Docker image
docker build -t aidial/ai-dial-admin-deployment-manager-backend:latest .
```

### Running

```bash
# Run the application with Gradle
./gradlew bootRun

# Run with Docker Compose (development mode)
# Note: docker-compose.yml is located in the local_env folder
cd local_env
docker-compose up

# This starts all containers including:
# - deployment-manager-backend (main application)
# - postgres (PostgreSQL database server)
# - sqlserver (MS SQL Server database)

# Stop Docker Compose
docker-compose down
```

## Testing

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.epam.aidial.deployment.manager.service.ImageDefinitionServiceTest"

# Run tests with specific pattern
./gradlew test --tests "*ControllerTest"
```

## Code Quality

```bash
# Run checkstyle checks
./gradlew checkstyle

# Check for compilation warnings (configured with -Werror)
./gradlew compileJava
```

## Architecture

### Core Components

**Pipeline-Based Image Building**: The application uses a multi-stage pipeline pattern for building container images with Kaniko or Buildkit in Kubernetes jobs. Key pipeline steps include base image building, image analysis, and wrapper image creation. For STDIO MCP servers, the system creates wrapper images by inserting an HTTP-to-STDIO proxy executable into user-provided base images. The proxy executable is pre-built separately (using PyInstaller for standalone executables) and the system detects Linux distributions (Alpine vs Debian) to select the appropriate proxy executable.

**Disposable Resource Management**: Critical pattern for tracking and cleaning up Kubernetes resources. Resources have lifecycle states (TEMPORARY, STABLE, TO_CLEANUP) and are automatically cleaned up using scheduled jobs with ShedLock for distributed coordination. All Kubernetes resources are tracked by group ID for comprehensive cleanup.

**Knative-Based Deployments**: Uses Knative Serving for serverless container deployments with auto-scaling. Services are deployed with automatic HTTPS endpoints and configurable scaling parameters. Separate namespaces for builds and deployments with configurable resource limits.

### Key Service Patterns

- **McpService**: Manages Model Context Protocol client connections using a factory pattern with automatic lifecycle management. Provides tools, resources, and prompts endpoints.

- **DeploymentService**: Handles Kubernetes deployments with sophisticated environment variable management - sensitive vars become K8s Secrets.

- **ImageBuildService**: Orchestrates the image build pipeline with real-time status updates via SSE (Server-Sent Events) and concurrent caching.

### Database Architecture

**Polymorphic Image Definitions**: Uses single table inheritance for McpImageDefinition, AdapterImageDefinition, and InterceptorImageDefinition with discriminator columns.

**Resource Tracking**: DisposableResource entities track all Kubernetes resources by group ID for comprehensive cleanup.

**Database Support**: Supports H2 (with AES encryption and configurable master keys and file encryption), PostgreSQL, and MS SQL Server databases.

## Development Notes

### Sensitive Data Handling

Sensitive environment variables are automatically converted to Kubernetes Secrets with Base64 encoding. These secrets are tracked as disposable resources and cleaned up automatically.

### Kubernetes Integration

Supports both config file and token-based K8s authentication with context switching:
- `K8S_CONNECT_TYPE=CONFIG_FILE` with kubeconfig
- `K8S_CONNECT_TYPE=TOKEN` with master URL and OAuth token

### Docker Registry

Configure registry authentication via `DOCKER_REGISTRY_AUTH` environment variable (NONE, BASIC, or other schemes).

## Configuration for Development

### Development Mode

For local development without authentication, set:
```bash
CONFIG_REST_SECURITY_MODE=none
```

Set this in `docker-compose.yml` for local development without authentication.

### H2 Database Credentials

For local development with H2 database, default credentials are provided in the `docker-compose.yml` file. 

To generate new H2 encryption credentials, you can use:

1. **Java Test Class**: Use the `com.epam.aidial.cfg.encryption.GenerateSecretsTest` class

2. **Python Script** (from [main admin repo](https://github.com/epam/ai-dial-admin-backend/blob/development/secrets-utils/keys_generator.py)):
   ```bash
   python secrets-utils/keys_generator.py
   ```
   This will generate values for:
   - `H2_DATASOURCE_PASSWORD`
   - `H2_DATASOURCE_MASTER_KEY`
   - `H2_DATASOURCE_ENCRYPTED_FILE_KEY`

### Kubernetes Configuration

Configure K8s connection via:
- `K8S_CONNECT_TYPE=CONFIG_FILE` with kubeconfig (default: `~/.kube/config`)
- `K8S_CONNECT_TYPE=TOKEN` with master URL (`K8S_MASTER_URL`) and OAuth token (`K8S_OAUTH_TOKEN`)

### Docker Registry Configuration

Configure registry authentication via `DOCKER_REGISTRY_AUTH` environment variable:
- `NONE` - No authentication
- `BASIC` - Basic authentication (requires `DOCKER_REGISTRY_USER` and `DOCKER_REGISTRY_PASSWORD`)

For complete configuration documentation, see [Configuration Documentation](configuration.md).

