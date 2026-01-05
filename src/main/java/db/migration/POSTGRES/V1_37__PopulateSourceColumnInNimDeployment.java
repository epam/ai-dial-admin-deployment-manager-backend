package db.migration.POSTGRES;

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
            statement.execute("ALTER TABLE nim_deployment ADD COLUMN source JSONB");
            log.info("Added source column to nim_deployment table");
        } catch (Exception e) {
            // Column might already exist, which is fine
            log.debug("Source column may already exist: {}", e.getMessage());
        }
    }

    @Override
    protected String getUpdateQuery() {
        return "UPDATE nim_deployment SET source = CAST(? AS jsonb) WHERE id = ?";
    }

    @Override
    protected void setUpdateStatementParams(PreparedStatement statement, String transformedJson, UUID deploymentId) throws Exception {
        statement.setString(1, transformedJson);
        statement.setObject(2, deploymentId);
    }

    @Override
    protected void setNotNullConstraint(Statement statement) throws Exception {
        statement.execute("ALTER TABLE nim_deployment ALTER COLUMN source SET NOT NULL");
        log.info("Set source column to NOT NULL");
    }
}

