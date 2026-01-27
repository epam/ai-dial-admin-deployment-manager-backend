package com.epam.aidial.deployment.manager.utils;

import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@UtilityClass
public class K8sNamingUtils {

    public static final String MCP_PREFIX = "mcp";
    public static final String DM_PREFIX = "dm";

    @Setter
    private static String resourceNamePrefix;

    public String extractName(HasMetadata k8sObject) {
        return k8sObject.getMetadata().getName();
    }

    @Deprecated
    public String extractMcpPrefixedId(String name) {
        return extractId(MCP_PREFIX, name);
    }

    public String extractId(String name) {
        return extractId(DM_PREFIX, name);
    }

    private String extractId(String defaultPrefix, String name) {
        String prefix = getResourceNamePrefixOrDefaultPrefix(defaultPrefix);
        try {
            String fullPrefix = prefix + "-";
            if (name.startsWith(fullPrefix)) {
                return name.substring(fullPrefix.length());
            } else {
                log.warn("K8s object name does not match expected format: {}", name);
                return null;
            }
        } catch (Exception e) {
            log.warn("Could not extract valid string ID from name: {}", name);
            return null;
        }
    }

    @Deprecated
    public String generateMcpPrefixedName(String name) {
        String prefix = getResourceNamePrefixOrDefaultPrefix(MCP_PREFIX);
        return "%s-%s".formatted(prefix, name);
    }

    public String generateName(String name) {
        String prefix = getResourceNamePrefixOrDefaultPrefix(DM_PREFIX);
        return "%s-%s".formatted(prefix, name);
    }

    public String generateName(String type, String name) {
        String prefix = getResourceNamePrefixOrDefaultPrefix(DM_PREFIX);
        return "%s-%s-%s".formatted(prefix, type, name);
    }

    public String generateUniqueName(String type, String name) {
        String prefix = getResourceNamePrefixOrDefaultPrefix(DM_PREFIX);
        return "%s-%s-%s-%s".formatted(prefix, type, name, randomStr(6));
    }

    private String randomStr(int length) {
        return RandomStringUtils.secure().nextAlphabetic(length).toLowerCase();
    }

    private String getResourceNamePrefixOrDefaultPrefix(String defaultPrefix) {
        return StringUtils.isNotBlank(resourceNamePrefix) ? resourceNamePrefix : defaultPrefix;
    }

}
