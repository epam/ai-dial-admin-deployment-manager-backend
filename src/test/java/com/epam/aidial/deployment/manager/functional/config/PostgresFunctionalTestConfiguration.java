package com.epam.aidial.deployment.manager.functional.config;

import com.epam.aidial.deployment.manager.functional.config.persistence.PostgresTestPersistenceService;
import com.epam.aidial.deployment.manager.functional.config.persistence.TestPersistenceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Import(FunctionalTestConfiguration.class)
public class PostgresFunctionalTestConfiguration {

    @Bean
    public TestPersistenceService testPersistenceService() {
        return new PostgresTestPersistenceService();
    }

}
