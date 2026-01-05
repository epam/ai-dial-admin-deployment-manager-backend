package com.epam.aidial.deployment.manager.model;

import io.fabric8.kubernetes.api.model.Secret;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class GitSecretConfig {
    /**
     * The Kubernetes Secret, or null if no credentials are needed.
     */
    Secret secret;

    /**
     * The name of the secret (for volume reference).
     */
    String secretName;

    /**
     * The name of the volume that will contain the secret.
     */
    String volumeName;

    /**
     * List of volume mount configurations for the init container.
     */
    List<VolumeMountConfig> volumeMounts;

    /**
     * Default file permissions for files in the secret volume (in octal format, e.g., 0600).
     * This sets the permissions for all files created from the secret when mounted as a volume.
     */
    Integer defaultMode;

    /**
     * Configuration for a single volume mount.
     */
    @Value
    @Builder
    public static class VolumeMountConfig {
        /**
         * Mount path in the container (e.g., "/root/.git-credentials").
         */
        String mountPath;

        /**
         * SubPath within the secret volume (e.g., ".git-credentials").
         */
        String subPath;

        /**
         * Whether the mount is read-only.
         */
        boolean readOnly;
    }
}

