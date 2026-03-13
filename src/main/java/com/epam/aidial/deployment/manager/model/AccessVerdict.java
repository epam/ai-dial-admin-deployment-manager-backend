package com.epam.aidial.deployment.manager.model;

/**
 * Cilium verdict for a domain access: allowed if any access was allowed, otherwise blocked.
 */
public enum AccessVerdict {
    ALLOWED,
    BLOCKED
}
