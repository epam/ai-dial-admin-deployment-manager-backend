package com.epam.aidial.deployment.manager.kubernetes.informer.registration;

public interface InformerRegistration {
    void start();

    void stop();

    String getResourceType();
}