package com.epam.aidial.deployment.manager.functional.config;

import com.epam.aidial.deployment.manager.functional.config.persistence.SqlServerTestPersistenceService;
import com.epam.aidial.deployment.manager.functional.config.persistence.TestPersistenceService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestConfiguration
@Import(FunctionalTestConfiguration.class)
public class SqlServerFunctionalTestConfiguration {

    @Bean
    public TestPersistenceService testPersistenceService() {
        return new SqlServerTestPersistenceService();
    }

}
