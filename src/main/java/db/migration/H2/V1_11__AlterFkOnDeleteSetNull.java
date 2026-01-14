package db.migration.H2;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

@Slf4j
public class V1_11__AlterFkOnDeleteSetNull extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();

        try (var statement = connection.createStatement()) {
            deleteForeignKeys(statement, "built_image");
            makeColumnNullable(statement, "built_image", "image_definition_id");
            addForeignKey(statement, "built_image", "fk_built_image_to_image_definition", "image_definition_id", "image_definition", "id");

            deleteForeignKeys(statement, "deployment");
            makeColumnNullable(statement, "deployment", "image_id");
            makeColumnNullable(statement, "deployment", "image_definition_id");
            addForeignKey(statement, "deployment", "fk_deployment_to_built_image", "image_id", "built_image", "id");
            addForeignKey(statement, "deployment", "fk_deployment_to_image_definition", "image_definition_id", "image_definition", "id");
        }
    }

    private void deleteForeignKeys(Statement statement, String tableName) throws Exception {
        var sqlFindConstraint = """
                SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                    WHERE TABLE_NAME = '%s' AND CONSTRAINT_TYPE = 'FOREIGN KEY'"""
                .formatted(tableName.toUpperCase());

        var constraintNames = new ArrayList<String>();
        try (ResultSet rs = statement.executeQuery(sqlFindConstraint)) {
            while (rs.next()) {
                constraintNames.add(rs.getString("CONSTRAINT_NAME"));
            }
        }

        if (constraintNames.isEmpty()) {
            throw new IllegalStateException("Could not find foreign key constraint on %s".formatted(tableName));
        }

        log.info("Found existing foreign key constraints: {}", constraintNames);

        for (var constraintName : constraintNames) {
            // Step 3: Drop the old constraint using the dynamically found name
            statement.execute("ALTER TABLE %s DROP CONSTRAINT %s".formatted(tableName, constraintName));
            log.info("Dropped constraint '{}'.", constraintName);
        }
    }

    private void makeColumnNullable(Statement statement, String tableName, String columnName) throws Exception {
        statement.execute("ALTER TABLE %s ALTER COLUMN %s SET NULL".formatted(tableName, columnName));
        log.info("Column '{}' on table {} set to nullable.", columnName, tableName);
    }

    private void addForeignKey(
            Statement statement,
            String tableName,
            String constraintName,
            String columnName,
            String referencedTableName,
            String referencedColumnName
    ) throws Exception {
        String sqlAddConstraint = """
                ALTER TABLE %s
                    ADD CONSTRAINT %s
                    FOREIGN KEY (%s)
                    REFERENCES %s(%s)
                    ON DELETE SET NULL"""
                .formatted(tableName, constraintName, columnName, referencedTableName, referencedColumnName);
        statement.execute(sqlAddConstraint);
        log.info("Added new constraint '{}' on table {} with ON DELETE SET NULL.", constraintName, tableName);
    }

}
