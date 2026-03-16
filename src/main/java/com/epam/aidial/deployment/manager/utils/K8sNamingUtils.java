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

    public static final String DM_PREFIX = "dm";

    @Setter
    private static String resourceNamePrefix;

    public String extractName(HasMetadata k8sObject) {
        return k8sObject.getMetadata().getName();
    }

    public String generateName(String name) {
        String prefix = getResourceNamePrefixOrDefaultPrefix();
        return "%s-%s".formatted(prefix, name);
    }

    public String generateName(String type, String name) {
        String prefix = getResourceNamePrefixOrDefaultPrefix();
        return "%s-%s-%s".formatted(prefix, type, name);
    }

    public String generateUniqueName(String type, String name) {
        String prefix = getResourceNamePrefixOrDefaultPrefix();
        return "%s-%s-%s-%s".formatted(prefix, type, name, randomStr(6));
    }

    private String randomStr(int length) {
        return RandomStringUtils.secure().nextAlphabetic(length).toLowerCase();
    }

    private String getResourceNamePrefixOrDefaultPrefix() {
        return StringUtils.isNotBlank(resourceNamePrefix) ? resourceNamePrefix : K8sNamingUtils.DM_PREFIX;
    }

}
