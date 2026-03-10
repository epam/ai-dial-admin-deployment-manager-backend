package db.migration.POSTGRES;

import db.migration.common.V1_50__UnifyDeploymentSourceBase;

import java.sql.PreparedStatement;
import java.sql.Statement;

public class V1_50__UnifyDeploymentSource extends V1_50__UnifyDeploymentSourceBase {

    @Override
    protected void addSourceColumn(Statement statement) throws Exception {
        statement.execute("ALTER TABLE deployment ADD COLUMN source jsonb");
    }

    @Override
    protected String getUpdateQuery() {
        return "UPDATE deployment SET source = CAST(? AS jsonb) WHERE id = ?";
    }

    @Override
    protected void setUpdateParams(PreparedStatement stmt, String sourceJson, String deploymentId) throws Exception {
        stmt.setString(1, sourceJson);
        stmt.setString(2, deploymentId);
    }

    @Override
    protected void dropOldColumns(Statement statement) throws Exception {
        // Drop old columns from deployment base table (keep image_definition_id for filtering)
        statement.execute("ALTER TABLE deployment DROP COLUMN IF EXISTS image_definition_type");
        statement.execute("ALTER TABLE deployment DROP COLUMN IF EXISTS image_definition_name");
        statement.execute("ALTER TABLE deployment DROP COLUMN IF EXISTS image_definition_version");

        // Drop source from nim_deployment and inference_deployment
        statement.execute("ALTER TABLE nim_deployment DROP COLUMN IF EXISTS source");
        statement.execute("ALTER TABLE inference_deployment DROP COLUMN IF EXISTS source");
    }
}
