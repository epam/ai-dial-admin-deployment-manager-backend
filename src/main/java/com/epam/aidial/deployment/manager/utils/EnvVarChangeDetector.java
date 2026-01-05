package com.epam.aidial.deployment.manager.utils;

import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.EnvVarValue;
import com.epam.aidial.deployment.manager.model.FileEnvVarValue;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@UtilityClass
public class EnvVarChangeDetector {

    /**
     * Compares a lists of environment variables with a list of environment variable definitions to determine if there are any changes.
     * This method properly compares each element regardless of their order in the lists.
     *
     * @param existingEnvs List of existing environment variables
     * @param metadata Object that contains updated environment variable definitions
     * @return true if there are changes in the environment variables, false otherwise
     */
    public static boolean areEnvsChanged(List<EnvVar> existingEnvs, DeploymentMetadata metadata) {
        if (metadata == null) {
            return !CollectionUtils.isEmpty(existingEnvs);
        }

        List<EnvVarDefinition> updatedEnvs = metadata.getEnvs();

        if (CollectionUtils.isEmpty(existingEnvs) && CollectionUtils.isEmpty(updatedEnvs)) {
            return false;
        }
        if (CollectionUtils.isNotEmpty(existingEnvs) && CollectionUtils.isEmpty(updatedEnvs)) {
            return true;
        }
        if (CollectionUtils.isEmpty(existingEnvs)) {
            return true;
        }
        if (existingEnvs.size() != updatedEnvs.size()) {
            return true;
        }

        Map<String, EnvVar> existingMap = existingEnvs.stream()
                .collect(Collectors.toMap(EnvVar::getName, Function.identity()));
        Map<String, EnvVarDefinition> updatedMap = updatedEnvs.stream()
                .collect(Collectors.toMap(EnvVarDefinition::getName, Function.identity()));

        if (!existingMap.keySet().equals(updatedMap.keySet())) {
            return true;
        }

        for (String name : existingMap.keySet()) {
            EnvVar existingVar = existingMap.get(name);
            EnvVarDefinition updatedVar = updatedMap.get(name);

            if (!isMountTypeCompatibleWithEnvVar(existingVar, updatedVar.getMountType())) {
                return true;
            }

            EnvVarValue existingValue = existingVar.getValue();
            EnvVarValue updatedValue = updatedVar.getValue();

            if (existingValue == null && updatedValue != null) {
                return true;
            }
            if (existingValue != null && updatedValue == null) {
                return true;
            }

            if (existingValue != null) {
                String existingStrValue = existingValue.getValue();
                String updatedStrValue = updatedValue.getValue();

                if (!Objects.equals(existingStrValue, updatedStrValue)) {
                    return true;
                }

                if (existingValue instanceof FileEnvVarValue existingFileValue
                        && updatedValue instanceof FileEnvVarValue updatedFileValue) {
                    if (!Objects.equals(existingFileValue.fileName(), updatedFileValue.fileName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isMountTypeCompatibleWithEnvVar(EnvVar envVar, EnvVarMountType mountType) {
        if (mountType == null) {
            return false;
        }

        return switch (mountType) {
            case CONTENT -> envVar instanceof SimpleEnvVar;
            case SECURE_CONTENT -> envVar instanceof SensitiveEnvVar && !(envVar instanceof SensitiveFileEnvVar);
            case SECURE_FILE -> envVar instanceof SensitiveFileEnvVar;
        };
    }
}
