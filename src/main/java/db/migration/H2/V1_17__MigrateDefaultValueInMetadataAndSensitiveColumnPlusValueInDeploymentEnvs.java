package db.migration.H2;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceEnvVarMountType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.h2.value.ValueJson;

import java.util.UUID;

@Slf4j
public class V1_17__MigrateDefaultValueInMetadataAndSensitiveColumnPlusValueInDeploymentEnvs extends BaseJavaMigration {

    private static final String IMAGE_DEFINITION_TABLE_NAME = "image_definition";
    private static final String BUILT_IMAGE_TABLE_NAME = "built_image";
    private static final String DEPLOYMENT_TABLE_NAME = "deployment";

    private static final String VALUE_PROPERTY = "value";
    private static final String DEFAULT_VALUE_PROPERTY = "defaultValue";
    private static final String SENSITIVE_PROPERTY = "sensitive";
    private static final String METADATA_PROPERTY = "metadata";
    private static final String ENVS_PROPERTY = "envs";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void migrate(Context context) {
        log.info("Starting migration V1.17...");
        migrateTable(context, IMAGE_DEFINITION_TABLE_NAME, METADATA_PROPERTY, DEFAULT_VALUE_PROPERTY);
        migrateTable(context, BUILT_IMAGE_TABLE_NAME, METADATA_PROPERTY, DEFAULT_VALUE_PROPERTY);
        migrateTable(context, DEPLOYMENT_TABLE_NAME, ENVS_PROPERTY, VALUE_PROPERTY);
        log.info("Successfully completed migration V1.17");
    }

    private void migrateTable(Context context, String tableName, String columnName, String valueProperty) {
        String selectSql = String.format("SELECT id, %s FROM %s WHERE %s IS NOT NULL", columnName, tableName, columnName);
        log.debug("Migrating table '%s'".formatted(tableName));

        try (var statement = context.getConnection().createStatement();
                var resultSet = statement.executeQuery(selectSql);
                var updateStatement = context.getConnection()
                        .prepareStatement(String.format("UPDATE %s SET %s = CAST(? AS JSON) WHERE id = ?", tableName, columnName))
        ) {
            while (resultSet.next()) {
                UUID id = resultSet.getObject("id", UUID.class);
                String jsonData = resultSet.getString(columnName);

                boolean updated = false;
                JsonNode jsonNode = MAPPER.readTree(jsonData);

                boolean inputCondition = getCondition(columnName, jsonNode);
                if (inputCondition) {
                    ArrayNode envs = (ArrayNode) (ENVS_PROPERTY.equals(columnName) ? jsonNode : jsonNode.get(ENVS_PROPERTY));
                    log.debug("Migrating row with ID '%s'. %s before update: %s".formatted(id, columnName, jsonData));
                    
                    for (int i = 0; i < envs.size(); i++) {
                        ObjectNode env = (ObjectNode) envs.get(i);

                        if (env.has(valueProperty) && env.get(valueProperty).isTextual()) {
                            String defaultValue = env.get(valueProperty).asText();

                            ObjectNode newDefaultValue = MAPPER.createObjectNode();
                            newDefaultValue.put("$type", "simple");
                            newDefaultValue.put("value", defaultValue);
                            
                            env.set(valueProperty, newDefaultValue);
                            updated = true;
                        }

                        if (METADATA_PROPERTY.equals(columnName) && env.has(SENSITIVE_PROPERTY) && env.get(SENSITIVE_PROPERTY).isBoolean()) {
                            boolean sensitive = env.get(SENSITIVE_PROPERTY).asBoolean();
                            PersistenceEnvVarMountType mountType = sensitive
                                    ? PersistenceEnvVarMountType.SECURE_CONTENT
                                    : PersistenceEnvVarMountType.CONTENT;

                            env.set("mountType", TextNode.valueOf(mountType.name()));
                            env.remove(SENSITIVE_PROPERTY);

                            updated = true;
                        }
                    }
                }
                
                if (updated) {
                    String updatedJson = MAPPER.writeValueAsString(jsonNode);
                    log.debug("Migrated row with ID '%s'. %s after update: %s".formatted(id, columnName, updatedJson));
                    updateStatement.setObject(1, ValueJson.fromJson(updatedJson));
                    updateStatement.setObject(2, id);
                    updateStatement.addBatch();
                }
            }
            
            updateStatement.executeBatch();
        } catch (Exception e) {
            String message = "Migration 1.17 failed. Reason: " + e.getMessage();
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    private boolean getCondition(String columnName, JsonNode jsonNode) {
        if (ENVS_PROPERTY.equals(columnName)) {
            return jsonNode.isArray();
        }
        if (METADATA_PROPERTY.equals(columnName)) {
            return jsonNode.has(ENVS_PROPERTY) && jsonNode.get(ENVS_PROPERTY).isArray();
        }
        return false;
    }

}