package com.epam.aidial.deployment.manager.service.deployment;

import io.fabric8.kubernetes.api.model.Pod;

@FunctionalInterface
interface ServicePodProvider {

    Pod getPod(String serviceName, String podName);
}
