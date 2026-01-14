package com.epam.aidial.deployment.manager.service;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.v1.Job;

import java.util.List;


public interface JobSpecification {

    String getJobId();

    String getNamespace();

    List<ConfigMap> getConfigMaps();

    List<Secret> getSecrets();

    Job getJob();

}
