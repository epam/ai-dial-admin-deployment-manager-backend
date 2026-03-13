package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.model.deployment.Deployment;

import java.util.Set;

@FunctionalInterface
interface IngressPortsResolver<D extends Deployment> {

    Set<Integer> resolve(D deployment);
}
