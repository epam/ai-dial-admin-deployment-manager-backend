package com.epam.aidial.deployment.manager.configuration.export;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Mix-in for SensitiveEnvVar export: exclude k8sSecretName, k8sSecretKey (generated on deployment creation).
 * Value is included or null depending on addSecrets (handled in exporter).
 */
public abstract class SensitiveEnvVarExportMixIn {

    @JsonIgnore
    abstract String getK8sSecretName();

    @JsonIgnore
    abstract String getK8sSecretKey();
}
