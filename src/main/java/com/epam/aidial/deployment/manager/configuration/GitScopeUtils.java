package com.epam.aidial.deployment.manager.configuration;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * Normalization helpers for git credential scopes (host + optional repository/project path),
 * shared between {@link GitConfiguration} (duplicate-scope validation) and
 * {@code GitService} (credential resolution) so both agree on what "the same scope" means.
 */
public final class GitScopeUtils {

    private GitScopeUtils() {
    }

    /**
     * Normalizes a git host for comparison: trimmed and lower-cased with {@link Locale#ROOT},
     * so the result never depends on the JVM's default locale (a Turkish-locale container would
     * otherwise fold {@code GITLAB.COM} to a dotless {@code gıtlab.com} and stop matching).
     *
     * @param host The configured or URL-derived host, possibly blank
     * @return The normalized host, or null if the input is blank
     */
    public static String normalizeHost(String host) {
        if (StringUtils.isBlank(host)) {
            return null;
        }
        return host.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Normalizes a git repository/project path for comparison: trimmed of surrounding whitespace,
     * leading and trailing {@code /}, and a trailing {@code .git} suffix. Casing is deliberately
     * preserved — git repository paths are case-sensitive, unlike hosts.
     *
     * @param path The configured or URL-derived path, possibly blank
     * @return The normalized path, or null if the input is blank or normalizes to nothing
     */
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
