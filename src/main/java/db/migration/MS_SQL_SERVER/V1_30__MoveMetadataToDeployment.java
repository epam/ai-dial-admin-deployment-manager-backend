package db.migration.MS_SQL_SERVER;

import db.migration.common.V1_30__MoveMetadataToDeploymentBase;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

@Slf4j
public class V1_30__MoveMetadataToDeployment extends V1_30__MoveMetadataToDeploymentBase {

    @Override
    protected void migrateMetadata(Context context) throws Exception {
        log.info("Migrating metadata from image_definition to deployment");
        
        String sql = """
            update d
            set metadata = i.metadata
            from deployment d
            join image_definition i on d.image_definition_id = i.id
            where d.metadata is null
                    """;
        
        try (var statement = context.getConnection().createStatement()) {
            statement.execute(sql);
        }
    }
    
    @Override
    protected PreparedStatement prepareUpdateStatement(Connection connection) throws Exception {
        return connection.prepareStatement("UPDATE deployment SET metadata = ? WHERE id = ?");
    }
    
    @Override
    protected void setUpdateStatementParams(PreparedStatement statement, String json, UUID id) throws Exception {
        statement.setString(1, json);
        statement.setObject(2, id);
    }
}