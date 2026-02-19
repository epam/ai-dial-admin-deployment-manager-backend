package com.epam.aidial.deployment.manager.service.config;

import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ExportSanitizer {

    @Qualifier("exportJsonMapper")
    private final JsonMapper exportJsonMapper;

    /**
     * Returns a copy of the deployment for export. When addSecrets is false and envs are present,
     * sensitive env var values in the copy are set to null. Callers may mutate the returned instance.
     */
    public Deployment sanitizeDeploymentForExport(Deployment source, boolean addSecrets) {
        if (addSecrets || CollectionUtils.isEmpty(source.getEnvs())) {
            return copyDeployment(source);
        }
        List<EnvVar> sanitizedEnvs = source.getEnvs().stream()
                .map(ExportSanitizer::clearSensitiveValueIfApplicable)
                .collect(Collectors.toList());
        return copyDeploymentWithEnvs(source, sanitizedEnvs);
    }

    private Deployment copyDeployment(Deployment source) {
        try {
            Class<? extends Deployment> clazz = source.getClass();
            return exportJsonMapper.readValue(exportJsonMapper.writeValueAsBytes(source), clazz);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to copy deployment '%s' for export".formatted(source.getId()), e);
        }
    }

    private static EnvVar clearSensitiveValueIfApplicable(EnvVar envVar) {
        if (envVar instanceof SensitiveFileEnvVar sensitiveFile) {
            return SensitiveFileEnvVar.builder()
                    .name(sensitiveFile.getName())
                    .value(null)
                    .k8sSecretName(null)
                    .k8sSecretKey(null)
                    .build();
        }
        if (envVar instanceof SensitiveEnvVar sensitive) {
            return SensitiveEnvVar.builder()
                    .name(sensitive.getName())
                    .value(null)
                    .k8sSecretName(null)
                    .k8sSecretKey(null)
                    .build();
        }
        return envVar;
    }

    private Deployment copyDeploymentWithEnvs(Deployment source, List<EnvVar> envs) {
        Deployment copy = copyDeployment(source);
        copy.setEnvs(envs);
        return copy;
    }
}
