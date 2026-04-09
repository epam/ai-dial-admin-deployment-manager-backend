# Quickstart: NIM Served Model Name Override

## What Changed

`NimManifestGenerator` now auto-injects the `NIM_SERVED_MODEL_NAME` environment variable into NIM deployment manifests when users don't explicitly provide it. The default value is the deployment identifier.

## How It Works

1. **User creates a NIM deployment** (no `NIM_SERVED_MODEL_NAME` in env vars) → system auto-sets `NIM_SERVED_MODEL_NAME=<deployment-id>` on the NIMService manifest.
2. **User creates a NIM deployment** (with explicit `NIM_SERVED_MODEL_NAME` in simple or sensitive env vars) → system preserves the user's value, no override.
3. **User updates a NIM deployment** → same logic applies on every manifest generation.

## Verify

```bash
./gradlew testFast --tests "com.epam.aidial.deployment.manager.service.manifest.NimManifestGeneratorTest"
```

## Files Modified

| File | Change |
|------|--------|
| `NimManifestGenerator.java` | Added `setServedModelNameIfNotSet()` method + constant |
| `NimManifestGeneratorTest.java` | Added 3 test cases for default injection and explicit override |
