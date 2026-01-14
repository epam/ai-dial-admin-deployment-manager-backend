package com.epam.aidial.deployment.manager.model;

import com.epam.aidial.deployment.manager.exception.UnknownObjectKindException;

public enum ObjectKind {
    POD,
    SERVICE,
    DEPLOYMENT,
    REPLICA_SET,
    STATEFUL_SET,
    DAEMON_SET,
    JOB,
    CRON_JOB,
    CONFIG_MAP,
    SECRET,
    INGRESS,
    NODE,
    NAMESPACE,
    PERSISTENT_VOLUME,
    PERSISTENT_VOLUME_CLAIM,
    STORAGE_CLASS,
    ENDPOINTS,
    EVENT,
    SERVICE_ACCOUNT,
    ROLE,
    ROLE_BINDING,
    CLUSTER_ROLE,
    CLUSTER_ROLE_BINDING,
    CUSTOM_RESOURCE_DEFINITION,
    REVISION,
    ROUTE,
    BUILD,
    BUILD_CONFIG,
    IMAGE_STREAM,
    TEMPLATE;

    public static ObjectKind fromString(String value) {
        if (value == null) {
            return null;
        }
        for (ObjectKind kind : ObjectKind.values()) {
            if (kind.name().equalsIgnoreCase(value)) {
                return kind;
            }
        }
        throw new UnknownObjectKindException("Unknown object kind: " + value);
    }
}
