package db.migration.MS_SQL_SERVER;

import db.migration.common.V1_37__PopulateSourceColumnInNimDeploymentBase;
import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

@Slf4j
public class V1_37__PopulateSourceColumnInNimDeployment extends V1_37__PopulateSourceColumnInNimDeploymentBase {

    @Override
    protected void addSourceColumn(Statement statement) {
        try {
            statement.execute("ALTER TABLE nim_deployment ADD source VARCHAR(MAX)");
            log.info("Added source column to nim_deployment table");
        } catch (Exception e) {
            // Column might already exist, which is fine
            log.debug("Source column may already exist: {}", e.getMessage());
        }
    }

    @Override
    protected void addInitialConstraints(Statement statement) {
        try {
            statement.execute("""
                    ALTER TABLE nim_deployment
                    ADD CONSTRAINT chk_nim_deployment_source_is_json 
                    CHECK (source IS NULL OR isjson(source) > 0)
                    """);
            log.info("Added JSON check constraint (allowing NULL)");
        } catch (Exception e) {
            // Constraint might already exist, which is fine
            log.debug("JSON check constraint may already exist: {}", e.getMessage());
        }
    }

    @Override
    protected String getUpdateQuery() {
        return "UPDATE nim_deployment SET source = ? WHERE id = ?";
    }

    @Override
    protected void setUpdateStatementParams(PreparedStatement statement, String transformedJson, UUID deploymentId) throws Exception {
        statement.setString(1, transformedJson);
        statement.setObject(2, deploymentId);
    }

    @Override
    protected void setNotNullConstraint(Statement statement) throws Exception {
        statement.execute("ALTER TABLE nim_deployment ALTER COLUMN source VARCHAR(MAX) NOT NULL");
        log.info("Set source column to NOT NULL");
    }

    @Override
    protected void updateConstraints(Statement statement) throws Exception {
        try {
            statement.execute("ALTER TABLE nim_deployment DROP CONSTRAINT chk_nim_deployment_source_is_json");
        } catch (Exception e) {
            log.debug("Could not drop constraint (may not exist): {}", e.getMessage());
        }

        statement.execute("""
                ALTER TABLE nim_deployment
                ADD CONSTRAINT chk_nim_deployment_source_is_json 
                CHECK (source IS NOT NULL AND isjson(source) > 0)
                """);
        log.info("Updated JSON check constraint to require NOT NULL");
    }
}

