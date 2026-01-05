package com.epam.aidial.deployment.manager.utils;

/**
 * Utility class for path operations.
 */
public class PathUtils {

    /**
     * Normalizes a base directory path by trimming whitespace and removing leading slash.
     * This ensures the path is relative and suitable for use as a context sub-path.
     *
     * @param baseDirectory The base directory path to normalize
     * @return The normalized path without leading slash
     */
    public static String normalizeBaseDirectory(String baseDirectory) {
        if (baseDirectory == null) {
            return null;
        }
        var normalized = baseDirectory.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}

