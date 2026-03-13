package com.epam.aidial.deployment.manager.service.deployment;

@FunctionalInterface
interface ServiceNameResolver {

    String resolve(String deploymentId);
}
