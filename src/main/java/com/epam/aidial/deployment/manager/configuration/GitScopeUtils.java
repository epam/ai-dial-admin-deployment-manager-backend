package com.epam.aidial.deployment.manager.configuration;

import org.apache.commons.lang3.StringUtils;

/**
 * Normalization helpers for git credential scopes (host + optional repository/project path),
 * shared between {@link GitConfiguration} (duplicate-scope validation) and
 * {@code GitService} (credential resolution) so both agree on what "the same scope" means.
 */
public final class GitScopeUtils {

    private GitScopeUtils() {
    }

    public static String normalizeHost(String host) {
        if (StringUtils.isBlank(host)) {
            return null;
        }
        return host.trim().toLowerCase();
    }

    public static String normalizePath(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        var normalized = path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - ".git".length());
        }
        return StringUtils.isBlank(normalized) ? null : normalized;
    }
}
