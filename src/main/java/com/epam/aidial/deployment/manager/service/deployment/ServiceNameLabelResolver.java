package com.epam.aidial.deployment.manager.service.deployment;

@FunctionalInterface
interface ServiceNameLabelResolver {

    String resolve(String deploymentId);
}
