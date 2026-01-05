package com.epam.aidial.deployment.manager.kubernetes.watcher;

public interface WatcherRegistration {

    void start();

    void stop();

    String getResourceType();
}