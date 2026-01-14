package com.epam.aidial.deployment.manager.kubernetes;

import lombok.experimental.UtilityClass;

/**
 * Constants for Kubernetes status field names used when extracting status information from resources.
 * These field names are used to access status data in Kubernetes resource metadata.
 */
@UtilityClass
public class KubernetesStatusConstants {

    /**
     * Field name for the state field in Kubernetes resource status.
     */
    public static final String STATE = "state";

    /**
     * Field name for the status field in Kubernetes resource metadata.
     */
    public static final String STATUS = "status";

}
