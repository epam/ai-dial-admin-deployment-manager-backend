# Data Model: NIM Served Model Name Override

## No Data Model Changes

This feature does not introduce any new entities, fields, relationships, or database schema changes. The served model name is injected at manifest generation time using the existing environment variable mechanism.

### Existing Entities (unchanged)

- **NimDeployment**: No new fields. The deployment identifier (`id`) is used as the default value for `NIM_SERVED_MODEL_NAME`.
- **SimpleEnvVar / SensitiveEnvVar**: Existing models used to pass environment variables. Users can provide `NIM_SERVED_MODEL_NAME` through either type to override the default.
- **NIMService (K8s CRD)**: The generated manifest's `spec.env[]` list receives the `NIM_SERVED_MODEL_NAME` entry — either auto-injected or user-provided.
