package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.model.deployment.Deployment;

@FunctionalInterface
interface DeploymentProvider<D extends Deployment> {

    D get(String id);
}
