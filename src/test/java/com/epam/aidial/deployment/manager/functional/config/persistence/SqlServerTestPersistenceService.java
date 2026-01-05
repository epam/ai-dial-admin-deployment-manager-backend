package com.epam.aidial.deployment.manager.functional.config.persistence;

import com.epam.aidial.deployment.manager.functional.SqlServerFunctionalTests;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class SqlServerTestPersistenceService implements TestPersistenceService {

    private static final String SNAPSHOT_DB_NAME = "test_snapshot";
    private static final String SNAPSHOT_FILE = "/tmp/" + SNAPSHOT_DB_NAME + ".ss";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dumpDb() {
        String sql = String.format(
                "CREATE DATABASE [%s] ON (NAME = %s, FILENAME = '%s') AS SNAPSHOT OF [%s];",
                SNAPSHOT_DB_NAME,
                SqlServerFunctionalTests.TEST_DB_NAME,
                SNAPSHOT_FILE,
                SqlServerFunctionalTests.TEST_DB_NAME
        );
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreDb() {
        String sql = String.format("""
                        USE MASTER;
                        ALTER DATABASE [%s] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
                        RESTORE DATABASE [%s] FROM DATABASE_SNAPSHOT = '%s';
                        ALTER DATABASE [%s] SET MULTI_USER WITH NO_WAIT;
                        USE [%s];""",
                SqlServerFunctionalTests.TEST_DB_NAME,
                SqlServerFunctionalTests.TEST_DB_NAME,
                SNAPSHOT_DB_NAME,
                SqlServerFunctionalTests.TEST_DB_NAME,
                SqlServerFunctionalTests.TEST_DB_NAME
        );
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupResources() {
        entityManager.createNativeQuery(String.format("DROP DATABASE [%s]", SNAPSHOT_DB_NAME)).executeUpdate();
    }
}
