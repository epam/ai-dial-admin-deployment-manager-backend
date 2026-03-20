package com.epam.aidial.deployment.manager.functional;

import com.epam.aidial.deployment.manager.functional.config.SqlServerFunctionalTestConfiguration;
import com.epam.aidial.deployment.manager.functional.tests.ConfigExportImportFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.ConfigImportValidationFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.DeploymentFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.FullWorkflowWithMockedK8sClientFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.ImageDefinitionBuildFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.ImageDefinitionFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.TopicFunctionalTest;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Nested;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.sql.Statement;

@DataJpaTest
@TestPropertySource(properties = {
        "datasource.vendor=MS_SQL_SERVER",
        "app.image-build-logs-size-limit=3",
        "app.config.export.file-name=config.json",
        "app.config.export.zip-name=export.zip",
        "app.knative.enabled=true",
        "app.nim.enabled=true",
        "app.kserve.enabled=true",
        "DOCKER_REGISTRY=test-docker-registry",
        "DOCKER_REGISTRY_AUTH=BASIC",
        "DOCKER_REGISTRY_USER=TestUser",
        "CILIUM_NETWORK_POLICIES_ENABLED=true",
        "MCP_PROXY_EXECUTABLE_IMAGE_DEBIAN=test-docker-registry.com/ai/dial/mcp_proxy_debian:latest",
})
@Import(SqlServerFunctionalTestConfiguration.class)
@Testcontainers
public class SqlServerFunctionalTests extends FunctionalTestSuite {

    public static final String TEST_DB_NAME = "test";

    @Container
    @ServiceConnection
    private static final MSSQLServerContainer<?> MS_SQL_SERVER = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-CU18-ubuntu-22.04");

    static {
        MS_SQL_SERVER.acceptLicense();
        MS_SQL_SERVER.start();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(MS_SQL_SERVER.getJdbcUrl());
        hikariConfig.setUsername(MS_SQL_SERVER.getUsername());
        hikariConfig.setPassword(MS_SQL_SERVER.getPassword());

        try (
                HikariDataSource dataSource = new HikariDataSource(hikariConfig);
                Statement statement = dataSource.getConnection().createStatement()
        ) {
            statement.execute("CREATE DATABASE [%s] COLLATE SQL_Latin1_General_CP1_CS_AS;".formatted(TEST_DB_NAME));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        String jdbcUrl = MS_SQL_SERVER.withUrlParam("database", TEST_DB_NAME).getJdbcUrl();
        registry.add("spring.datasource.url", () -> jdbcUrl);
    }

    @Nested
    class ImageDefinitionTests extends ImageDefinitionFunctionalTest {
    }

    @Nested
    class ImageDefinitionBuildTests extends ImageDefinitionBuildFunctionalTest {
    }

    @Nested
    class DeploymentTests extends DeploymentFunctionalTest {
    }

    @Nested
    class TopicTests extends TopicFunctionalTest {
    }

    @Nested
    class FullWorkflowWithMockedK8sClientTest extends FullWorkflowWithMockedK8sClientFunctionalTest {
    }

    @Nested
    class ConfigExportImportTests extends ConfigExportImportFunctionalTest {
    }

    @Nested
    class ConfigImportValidationTests extends ConfigImportValidationFunctionalTest {
    }
}
