# Quickstart: NIM KServe Migration & Configurable Storage Size

## Verify

```bash
./gradlew testFast                      # Fast tests (H2)
./gradlew checkstyleMain checkstyleTest # Code style
```

## API Usage

### Create NIM deployment with custom storage size

```json
POST /api/v1/deployments
{
  "type": "NIM",
  "name": "my-nim-model",
  "displayName": "My NIM Model",
  "source": {
    "$type": "ngc_registry",
    "imageRef": "nvcr.io/nvidia/nim/meta-llama3-8b-instruct:1.0"
  },
  "storageSize": "50Gi"
}
```

### Create NIM deployment with default storage (20Gi)

```json
POST /api/v1/deployments
{
  "type": "NIM",
  "name": "my-nim-model",
  "source": {
    "$type": "ngc_registry",
    "imageRef": "nvcr.io/nvidia/nim/meta-llama3-8b-instruct:1.0"
  }
}
```

### Response includes storageSize

```json
GET /api/v1/deployments/my-nim-model
{
  "name": "my-nim-model",
  "type": "NIM",
  "source": {
    "$type": "ngc_registry",
    "imageRef": "nvcr.io/nvidia/nim/meta-llama3-8b-instruct:1.0"
  },
  "storageSize": "50Gi",
  "status": "NOT_DEPLOYED",
  ...
}
```

## Configuration

| Property | Env Var | Default | Description |
|----------|---------|---------|-------------|
| `app.validation.resources.max-storage-size` | `RESOURCES_STORAGE_MAX_SIZE` | `200Gi` | Maximum allowed storage size for NIM deployments |

## Accepted storageSize formats

- Binary suffixes: `20Gi`, `500Mi`, `1Ti`, `100Ki`, `1Pi`, `1Ei`
- Decimal suffixes: `20G`, `500M`, `1T`, `100k`
- Plain bytes: `21474836480`
- Fractional: `0.5Gi`
