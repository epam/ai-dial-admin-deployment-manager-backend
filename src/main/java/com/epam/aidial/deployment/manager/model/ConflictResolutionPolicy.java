package com.epam.aidial.deployment.manager.model;

/**
 * Policy for resolving conflicts when importing config (e.g. image definition or deployment already exists).
 * <ul>
 *   <li>{@link #FAIL_IF_EXISTS} - abort import when a matching entity exists</li>
 *   <li>{@link #SKIP_IF_EXISTS} - skip importing the conflicting entity, keep existing</li>
 *   <li>{@link #OVERWRITE} - replace existing entity with imported one</li>
 * </ul>
 */
public enum ConflictResolutionPolicy {
    FAIL_IF_EXISTS,
    SKIP_IF_EXISTS,
    OVERWRITE,
}
