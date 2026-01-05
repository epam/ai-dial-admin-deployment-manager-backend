package com.epam.aidial.deployment.manager.functional;

import com.epam.aidial.deployment.manager.functional.config.persistence.TestPersistenceService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class FunctionalTestSuite {

    @Autowired
    private TestPersistenceService persistenceService;

    @BeforeAll
    void beforeAllTests() {
        persistenceService.dumpDb();
    }

    @AfterEach
    void afterEachTest() {
        persistenceService.restoreDb();
    }

    @AfterAll
    void afterAllTests() {
        persistenceService.cleanupResources();
    }
}
