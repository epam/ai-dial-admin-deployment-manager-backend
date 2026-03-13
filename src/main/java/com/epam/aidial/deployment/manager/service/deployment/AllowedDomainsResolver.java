package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.model.deployment.Deployment;

import java.util.List;

@FunctionalInterface
interface AllowedDomainsResolver<D extends Deployment> {

    List<String> resolve(D deployment);
}
