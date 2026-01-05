package com.epam.aidial.deployment.manager.kubernetes;

import lombok.experimental.UtilityClass;

/**
 * Constants for Kubernetes condition statuses and types.
 * These values are standardized across Kubernetes resources.
 */
@UtilityClass
public class KubernetesConditionConstants {

    /**
     * Condition status indicating a condition is true.
     */
    public static final String STATUS_TRUE = "True";

    /**
     * Condition status indicating a condition is false.
     */
    public static final String STATUS_FALSE = "False";

    /**
     * Condition type indicating a resource is ready.
     */
    public static final String CONDITION_READY = "Ready";

}

