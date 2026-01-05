package com.epam.aidial.deployment.manager.service;

public interface SafeAutoCloseable extends AutoCloseable {
    @Override
    void close();
}
