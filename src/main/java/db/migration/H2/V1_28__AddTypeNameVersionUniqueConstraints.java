package db.migration.H2;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

@Slf4j
public class V1_28__AddTypeNameVersionUniqueConstraints extends BaseJavaMigration {

    private static final String IMAGE_DEFINITION_TABLE = "IMAGE_DEFINITION";
    private static final String NAME_COLUMN = "NAME";

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();

        try (var statement = connection.createStatement()) {
            // Drop existing unique constraint on image_definition.name
            dropUniqueConstraintsOnColumn(statement, IMAGE_DEFINITION_TABLE, NAME_COLUMN);

            // Add type column to image_definition table
            statement.execute("ALTER TABLE IMAGE_DEFINITION ADD COLUMN TYPE VARCHAR(20)");
            log.info("Added TYPE column to IMAGE_DEFINITION table");

            // Update type column based on the entity type
            statement.execute("UPDATE IMAGE_DEFINITION ID SET TYPE = 'MCP' WHERE ID IN (SELECT ID FROM MCP_IMAGE_DEFINITION)");
            statement.execute("UPDATE IMAGE_DEFINITION ID SET TYPE = 'ADAPTER' WHERE ID IN (SELECT ID FROM ADAPTER_IMAGE_DEFINITION)");
            statement.execute("UPDATE IMAGE_DEFINITION ID SET TYPE = 'INTERCEPTOR' WHERE ID IN (SELECT ID FROM INTERCEPTOR_IMAGE_DEFINITION)");
            statement.execute("UPDATE IMAGE_DEFINITION ID SET TYPE = 'NIM' WHERE ID IN (SELECT ID FROM NIM_IMAGE_DEFINITION)");
            log.info("Updated TYPE column values based on entity types");

            // Make type column not nullable
            statement.execute("ALTER TABLE IMAGE_DEFINITION ALTER COLUMN TYPE SET NOT NULL");
            log.info("Made TYPE column NOT NULL");

            // Add unique constraint on (type, name, version) for image_definition
            addUniqueConstraint(statement, IMAGE_DEFINITION_TABLE, "uq_image_definition_type_name_version", "type", "name", "version");

            log.info("Successfully completed migration V1.28 - Added type-name-version unique constraints");
        }
    }

    /**
     * Finds and drops unique constraints that involve the specified column.
     * Uses INFORMATION_SCHEMA to find constraints dynamically.
     */
    private void dropUniqueConstraintsOnColumn(Statement statement, String tableName, String columnName) throws Exception {
        // Find unique constraints on the table that involve the specified column
        // We need to join TABLE_CONSTRAINTS with KEY_COLUMN_USAGE to find constraints involving specific columns
        var sqlFindConstraints = """
                SELECT DISTINCT tc.CONSTRAINT_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                    ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                    AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.TABLE_NAME = '%s'
                  AND tc.CONSTRAINT_TYPE = 'UNIQUE'
                  AND kcu.COLUMN_NAME = '%s'
                """
                .formatted(tableName, columnName);

        var constraintNames = new ArrayList<String>();
        try (ResultSet rs = statement.executeQuery(sqlFindConstraints)) {
            while (rs.next()) {
                constraintNames.add(rs.getString("CONSTRAINT_NAME"));
            }
        }

        if (constraintNames.isEmpty()) {
            log.info("No unique constraints found on column '{}' in table '{}'. Skipping drop.", columnName, tableName);
            return;
        }

        log.info("Found unique constraints on column '{}' in table '{}': {}", columnName, tableName, constraintNames);

        for (var constraintName : constraintNames) {
            try {
                statement.execute("ALTER TABLE %s DROP CONSTRAINT %s".formatted(tableName, constraintName));
                log.info("Dropped constraint '{}' from table '{}'.", constraintName, tableName);
            } catch (Exception e) {
                log.warn("Failed to drop constraint '{}' from table '{}': {}. Continuing...", constraintName, tableName, e.getMessage());
                // Continue with other constraints even if one fails
            }
        }
    }

    /**
     * Adds a unique constraint on the specified columns.
     */
    private void addUniqueConstraint(
            Statement statement,
            String tableName,
            String constraintName,
            String... columnNames
    ) throws Exception {
        if (columnNames.length == 0) {
            throw new IllegalArgumentException("At least one column name must be specified");
        }

        var columns = String.join(", ", columnNames);
        var sqlAddConstraint = """
                ALTER TABLE %s
                    ADD CONSTRAINT %s UNIQUE (%s)
                """
                .formatted(tableName, constraintName, columns);

        statement.execute(sqlAddConstraint);
        log.info("Added unique constraint '{}' on columns ({}) to table '{}'.", constraintName, columns, tableName);
    }
}

