package db.migration.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

@Slf4j
public abstract class V1_30__MoveMetadataToDeploymentBase extends BaseJavaMigration {

    protected static final String DEPLOYMENT_TABLE = "deployment";
    protected static final String IMAGE_DEFINITION_TABLE = "image_definition";
    protected static final String METADATA_COLUMN = "metadata";
    protected static final String ENVS_PROPERTY = "envs";
    protected static final String DEFAULT_VALUE_PROPERTY = "defaultValue";
    protected static final String REQUIRED_PROPERTY = "required";

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        log.info("Starting migration V1.30...");

        // Migrate metadata from image_definition to deployment
        migrateMetadata(context);

        // Clean up metadata.envs in deployment (remove defaultValue and required)
        cleanupMetadataEnvs(context);

        // Drop metadata column from image_definition
        dropMetadataColumn(context);

        log.info("Successfully completed migration V1.30");
    }

    protected abstract void migrateMetadata(Context context) throws Exception;

    protected void cleanupMetadataEnvs(Context context) throws Exception {
        log.info("Cleaning up metadata.envs in 'deployment' table");

        String selectSql = "SELECT id, metadata FROM deployment WHERE metadata IS NOT NULL";

        try (var statement = context.getConnection().createStatement();
                var resultSet = statement.executeQuery(selectSql)) {

            try (var updateStatement = prepareUpdateStatement(context.getConnection())) {
                int updatedCount = 0;

                while (resultSet.next()) {
                    UUID id = resultSet.getObject("id", UUID.class);
                    String jsonData = resultSet.getString(METADATA_COLUMN);

                    if (jsonData == null) {
                        continue;
                    }

                    JsonNode jsonNode = MAPPER.readTree(jsonData);
                    boolean updated = false;

                    if (jsonNode.has(ENVS_PROPERTY) && jsonNode.get(ENVS_PROPERTY).isArray()) {
                        ArrayNode envs = (ArrayNode) jsonNode.get(ENVS_PROPERTY);

                        for (int i = 0; i < envs.size(); i++) {
                            ObjectNode env = (ObjectNode) envs.get(i);

                            // Remove defaultValue and required fields
                            if (env.has(DEFAULT_VALUE_PROPERTY)) {
                                env.remove(DEFAULT_VALUE_PROPERTY);
                                updated = true;
                            }

                            if (env.has(REQUIRED_PROPERTY)) {
                                env.remove(REQUIRED_PROPERTY);
                                updated = true;
                            }
                        }
                    }

                    if (updated) {
                        String updatedJson = MAPPER.writeValueAsString(jsonNode);
                        setUpdateStatementParams(updateStatement, updatedJson, id);
                        updateStatement.addBatch();
                        updatedCount++;
                    }
                }

                if (updatedCount > 0) {
                    updateStatement.executeBatch();
                    log.info("Updated metadata for {} deployments", updatedCount);
                }
            }
        }
    }

    protected abstract PreparedStatement prepareUpdateStatement(Connection connection) throws Exception;

    protected abstract void setUpdateStatementParams(PreparedStatement statement, String json, UUID id) throws Exception;

    protected void dropMetadataColumn(Context context) throws Exception {
        log.info("Dropping metadata column from image_definition");

        try (var statement = context.getConnection().createStatement()) {
            statement.execute("alter table image_definition drop column metadata");
        }
    }
}