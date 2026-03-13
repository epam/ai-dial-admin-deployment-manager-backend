package com.epam.aidial.deployment.manager.service.deployment;

import io.fabric8.kubernetes.api.model.Pod;

import java.util.List;

@FunctionalInterface
interface ServicePodsProvider {

    List<Pod> getPods(String serviceName);
}
