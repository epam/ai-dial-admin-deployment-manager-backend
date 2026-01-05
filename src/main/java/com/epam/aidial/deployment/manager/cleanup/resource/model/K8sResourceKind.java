package com.epam.aidial.deployment.manager.cleanup.resource.model;

public enum K8sResourceKind {
    SECRET,
    CONFIGMAP,
    JOB,
    KNATIVE_SERVICE,
    NIM_SERVICE,
    INFERENCE_SERVICE,
    CILIUM_NETWORK_POLICY,
}
