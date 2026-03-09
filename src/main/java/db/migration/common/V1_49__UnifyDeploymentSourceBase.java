package db.migration.common;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.Statement;

@Slf4j
public abstract class V1_49__UnifyDeploymentSourceBase extends BaseJavaMigration {

    protected static final ObjectMapper MAPPER = JsonMapperConfiguration.createJsonMapper();

    @Override
    public void migrate(Context context) throws Exception {
        log.info("Unifying deployment source columns into deployment.source JSON");

        try (Statement statement = context.getConnection().createStatement()) {
            // Step 1: Add source JSON column to deployment table
            addSourceColumn(statement);

            // Step 2: Migrate data
            migrateNimSources(context);
            migrateInferenceSources(context);
            migrateInternalImageSources(context);

            // Step 3: Drop old columns
            dropOldColumns(statement);
        }
    }

    protected abstract void addSourceColumn(Statement statement) throws Exception;

    private void migrateNimSources(Context context) throws Exception {
        String selectQuery = "SELECT nd.id, nd.source FROM nim_deployment nd WHERE nd.source IS NOT NULL";
        String updateQuery = getUpdateQuery();

        try (var selectStmt = context.getConnection().createStatement();
                var updateStmt = context.getConnection().prepareStatement(updateQuery)) {
            var rs = selectStmt.executeQuery(selectQuery);
            int count = 0;
            while (rs.next()) {
                String id = rs.getString("id");
                String source = rs.getString("source");
                setUpdateParams(updateStmt, source, id);
                updateStmt.addBatch();
                count++;
            }
            if (count > 0) {
                updateStmt.executeBatch();
                log.info("Migrated {} NIM deployment sources", count);
            }
        }
    }

    private void migrateInferenceSources(Context context) throws Exception {
        String selectQuery = "SELECT ie.id, ie.source FROM inference_deployment ie WHERE ie.source IS NOT NULL";
        String updateQuery = getUpdateQuery();

        try (var selectStmt = context.getConnection().createStatement();
                var updateStmt = context.getConnection().prepareStatement(updateQuery)) {
            var rs = selectStmt.executeQuery(selectQuery);
            int count = 0;
            while (rs.next()) {
                String id = rs.getString("id");
                String source = rs.getString("source");
                setUpdateParams(updateStmt, source, id);
                updateStmt.addBatch();
                count++;
            }
            if (count > 0) {
                updateStmt.executeBatch();
                log.info("Migrated {} Inference deployment sources", count);
            }
        }
    }

    private void migrateInternalImageSources(Context context) throws Exception {
        // Migrate remaining deployments that have image_definition_id but no source yet
        String selectQuery = """
                SELECT d.id, d.image_definition_id, d.image_definition_type,
                       d.image_definition_name, d.image_definition_version
                FROM deployment d
                WHERE d.image_definition_id IS NOT NULL AND d.source IS NULL
                """;
        String updateQuery = getUpdateQuery();

        try (var selectStmt = context.getConnection().createStatement();
                var updateStmt = context.getConnection().prepareStatement(updateQuery)) {
            var rs = selectStmt.executeQuery(selectQuery);
            int count = 0;
            while (rs.next()) {
                String id = rs.getString("id");
                String imageDefId = rs.getString("image_definition_id");
                String imageDefType = rs.getString("image_definition_type");
                String imageDefName = rs.getString("image_definition_name");
                String imageDefVersion = rs.getString("image_definition_version");

                ObjectNode sourceNode = MAPPER.createObjectNode();
                sourceNode.put("$type", "internal_image");
                sourceNode.put("imageDefinitionId", imageDefId);
                if (imageDefType != null) {
                    sourceNode.put("imageDefinitionType", imageDefType);
                }
                if (imageDefName != null) {
                    sourceNode.put("imageDefinitionName", imageDefName);
                }
                if (imageDefVersion != null) {
                    sourceNode.put("imageDefinitionVersion", imageDefVersion);
                }
                String sourceJson = MAPPER.writeValueAsString(sourceNode);
                setUpdateParams(updateStmt, sourceJson, id);
                updateStmt.addBatch();
                count++;
            }
            if (count > 0) {
                updateStmt.executeBatch();
                log.info("Migrated {} internal image sources", count);
            }
        }
    }

    protected abstract String getUpdateQuery();

    protected abstract void setUpdateParams(PreparedStatement stmt, String sourceJson, String deploymentId) throws Exception;

    protected abstract void dropOldColumns(Statement statement) throws Exception;
}
