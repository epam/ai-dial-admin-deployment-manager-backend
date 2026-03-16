package db.migration.common;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
public abstract class V1_53__BackfillServiceNameBase extends BaseJavaMigration {

    private static final String MCP_PREFIX = "mcp";
    private static final String DM_PREFIX = "dm";

    private static final String SELECT_ACTIVE_DEPLOYMENTS =
            "SELECT d.id FROM deployment d WHERE d.status NOT IN ('NOT_DEPLOYED', 'STOPPED') AND d.service_name IS NULL";

    @Override
    public void migrate(Context context) throws Exception {
        log.info("Backfilling service_name for active deployments");

        var resourceNamePrefix = System.getenv("RESOURCE_NAME_PREFIX");
        var connection = context.getConnection();
        int count = 0;

        try (Statement selectStmt = connection.createStatement();
                ResultSet rs = selectStmt.executeQuery(SELECT_ACTIVE_DEPLOYMENTS);
                PreparedStatement updateStmt = connection.prepareStatement(
                        "UPDATE deployment SET service_name = ? WHERE id = ?")) {

            while (rs.next()) {
                var deploymentId = rs.getString("id");
                var prefix = resolvePrefix(connection, deploymentId, resourceNamePrefix);
                var serviceName = prefix + "-" + deploymentId;

                updateStmt.setString(1, serviceName);
                updateStmt.setString(2, deploymentId);
                updateStmt.addBatch();
                count++;
            }

            if (count > 0) {
                updateStmt.executeBatch();
            }
        }

        log.info("Backfilled service_name for {} active deployments", count);
    }

    private String resolvePrefix(Connection connection, String deploymentId, String resourceNamePrefix) throws Exception {
        if (StringUtils.isNotBlank(resourceNamePrefix)) {
            return resourceNamePrefix;
        }
        return isInferenceDeployment(connection, deploymentId) ? DM_PREFIX : MCP_PREFIX;
    }

    private boolean isInferenceDeployment(Connection connection, String deploymentId) throws Exception {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT 1 FROM inference_deployment WHERE id = ?")) {
            stmt.setString(1, deploymentId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}
