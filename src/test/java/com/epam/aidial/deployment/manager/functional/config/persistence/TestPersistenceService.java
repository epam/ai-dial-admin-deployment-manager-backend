package com.epam.aidial.deployment.manager.functional.config.persistence;

public interface TestPersistenceService {

    void dumpDb();

    void restoreDb();

    void cleanupResources();
}
