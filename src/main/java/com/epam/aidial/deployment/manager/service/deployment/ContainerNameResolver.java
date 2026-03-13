package com.epam.aidial.deployment.manager.service.deployment;

import io.fabric8.kubernetes.api.model.Pod;

@FunctionalInterface
interface ContainerNameResolver {

    String resolve(Pod pod);
}
