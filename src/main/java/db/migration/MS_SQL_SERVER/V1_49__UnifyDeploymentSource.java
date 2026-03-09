package db.migration.MS_SQL_SERVER;

import db.migration.common.V1_49__UnifyDeploymentSourceBase;

import java.sql.PreparedStatement;
import java.sql.Statement;

public class V1_49__UnifyDeploymentSource extends V1_49__UnifyDeploymentSourceBase {

    @Override
    protected void addSourceColumn(Statement statement) throws Exception {
        statement.execute("ALTER TABLE deployment ADD source varchar(max)");
        statement.execute("""
                ALTER TABLE deployment
                    ADD CONSTRAINT chk_deployment_source_is_json
                    CHECK (source IS NULL OR isjson(source) > 0)
                """);
    }

    @Override
    protected String getUpdateQuery() {
        return "UPDATE deployment SET source = ? WHERE id = ?";
    }

    @Override
    protected void setUpdateParams(PreparedStatement stmt, String sourceJson, String deploymentId) throws Exception {
        stmt.setString(1, sourceJson);
        stmt.setString(2, deploymentId);
    }

    @Override
    protected void dropOldColumns(Statement statement) throws Exception {
        // Drop old columns from deployment base table (keep image_definition_id for filtering)
        dropColumnIfExists(statement, "deployment", "image_definition_type");
        dropColumnIfExists(statement, "deployment", "image_definition_name");
        dropColumnIfExists(statement, "deployment", "image_definition_version");

        // Drop source from nim_deployment and inference_deployment
        dropColumnIfExists(statement, "nim_deployment", "source");
        dropColumnIfExists(statement, "inference_deployment", "source");
    }

    private void dropColumnIfExists(Statement statement, String table, String column) throws Exception {
        // SQL Server requires dropping all constraints referencing a column before dropping it
        dropConstraintsForColumn(statement, table, column);
        statement.execute(
                "IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('" + table
                        + "') AND name = '" + column + "') ALTER TABLE " + table + " DROP COLUMN " + column
        );
    }

    private void dropConstraintsForColumn(Statement statement, String table, String column) throws Exception {
        // Drop default constraints
        statement.execute(
                "DECLARE @dc NVARCHAR(128); "
                        + "SELECT @dc = dc.name "
                        + "FROM sys.default_constraints dc "
                        + "JOIN sys.columns c ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id "
                        + "WHERE dc.parent_object_id = OBJECT_ID('" + table + "') AND c.name = '" + column + "'; "
                        + "IF @dc IS NOT NULL EXEC('ALTER TABLE " + table + " DROP CONSTRAINT ' + @dc);"
        );
        // Drop check constraints that reference this column
        statement.execute(
                "DECLARE @cc NVARCHAR(MAX) = ''; "
                        + "SELECT @cc = @cc + 'ALTER TABLE " + table + " DROP CONSTRAINT ' + cc.name + '; ' "
                        + "FROM sys.check_constraints cc "
                        + "JOIN sys.columns c ON cc.parent_object_id = c.object_id AND cc.parent_column_id = c.column_id "
                        + "WHERE cc.parent_object_id = OBJECT_ID('" + table + "') AND c.name = '" + column + "'; "
                        + "IF @cc <> '' EXEC(@cc);"
        );
    }
}
