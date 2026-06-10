package db.migration.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import db.migration.MigrationJsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

@Slf4j
public abstract class V1_37__PopulateSourceColumnInNimDeploymentBase extends BaseJavaMigration {

    protected static final ObjectMapper MAPPER = MigrationJsonMapper.createJsonMapper();
    protected static final String DOCKER_TYPE = "docker";
    protected static final String NGC_REGISTRY_TYPE = "ngc_registry";
    protected static final String TYPE_FIELD = "$type";
    protected static final String IMAGE_URI_FIELD = "imageUri";
    protected static final String IMAGE_REF_FIELD = "imageRef";

    @Override
    public void migrate(Context context) throws Exception {
        log.info("Adding and populating source column in nim_deployment from image_definition");

        try (Statement statement = context.getConnection().createStatement()) {
            // Step 1: Add the source column
            addSourceColumn(statement);

            // Step 2: Add database-specific constraints (if any)
            addInitialConstraints(statement);

            // Step 3: Populate the column
            populateSourceColumn(context);

            // Step 4: Set NOT NULL constraint
            setNotNullConstraint(statement);

            // Step 5: Update constraints (if needed)
            updateConstraints(statement);
        }
    }

    protected abstract void addSourceColumn(Statement statement) throws Exception;

    protected void addInitialConstraints(Statement statement) throws Exception {
        // Default implementation does nothing - override in SQL Server
    }

    protected void populateSourceColumn(Context context) throws Exception {
        String selectQuery = """
                SELECT d.id AS deployment_id, img_def.source
                FROM nim_deployment nd
                JOIN deployment d ON d.id = nd.id
                JOIN image_definition img_def ON d.image_definition_id = img_def.id
                WHERE img_def.source IS NOT NULL
                  AND nd.source IS NULL
                """;

        String updateQuery = getUpdateQuery();

        try (var selectStatement = context.getConnection().createStatement();
                var updateStatement = context.getConnection().prepareStatement(updateQuery)) {

            var resultSet = selectStatement.executeQuery(selectQuery);
            int processedCount = 0;
            int errorCount = 0;

            while (resultSet.next()) {
                UUID deploymentId = resultSet.getObject("deployment_id", UUID.class);
                String sourceJson = resultSet.getString("source");

                try {
                    String transformedJson = transformDockerSourceToNgcRegistry(sourceJson);
                    setUpdateStatementParams(updateStatement, transformedJson, deploymentId);
                    updateStatement.addBatch();
                    processedCount++;
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to transform source for deployment {}: {}", deploymentId, e.getMessage(), e);
                }
            }

            if (processedCount > 0) {
                updateStatement.executeBatch();
                log.info("Successfully populated source column for {} nim_deployment records", processedCount);
            }

            if (errorCount > 0) {
                log.warn("Failed to process {} nim_deployment records", errorCount);
            }
        }
    }

    protected abstract String getUpdateQuery();

    protected abstract void setUpdateStatementParams(PreparedStatement statement, String transformedJson, UUID deploymentId) throws Exception;

    protected abstract void setNotNullConstraint(Statement statement) throws Exception;

    protected void updateConstraints(Statement statement) throws Exception {
        // Default implementation does nothing - override in SQL Server
    }

    protected String transformDockerSourceToNgcRegistry(String dockerSourceJson) {
        if (dockerSourceJson == null || dockerSourceJson.trim().isEmpty()) {
            throw new IllegalArgumentException("Source JSON cannot be null or empty");
        }

        try {
            JsonNode rootNode = MAPPER.readTree(dockerSourceJson);

            if (!rootNode.has(TYPE_FIELD)) {
                throw new IllegalArgumentException("Source JSON missing required field: " + TYPE_FIELD);
            }

            String sourceType = rootNode.get(TYPE_FIELD).asText();
            if (!DOCKER_TYPE.equalsIgnoreCase(sourceType)) {
                throw new IllegalArgumentException(
                        String.format("Expected source type '%s', but found '%s'", DOCKER_TYPE, sourceType)
                );
            }

            if (!rootNode.has(IMAGE_URI_FIELD)) {
                throw new IllegalArgumentException("Source JSON missing required field: " + IMAGE_URI_FIELD);
            }

            String imageUri = rootNode.get(IMAGE_URI_FIELD).asText();
            if (imageUri == null || imageUri.trim().isEmpty()) {
                throw new IllegalArgumentException("Image URI cannot be null or empty");
            }

            JsonNode ngcRegistrySource = MAPPER.createObjectNode()
                    .put(TYPE_FIELD, NGC_REGISTRY_TYPE)
                    .put(IMAGE_REF_FIELD, imageUri);

            return MAPPER.writeValueAsString(ngcRegistrySource);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse source JSON: {}", dockerSourceJson, e);
            throw new RuntimeException("Failed to transform source JSON from docker to ngc_registry format", e);
        }
    }
}

