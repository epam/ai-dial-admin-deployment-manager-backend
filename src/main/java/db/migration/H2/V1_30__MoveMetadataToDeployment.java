package db.migration.H2;

import db.migration.common.V1_30__MoveMetadataToDeploymentBase;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.Context;
import org.h2.value.ValueJson;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

@Slf4j
public class V1_30__MoveMetadataToDeployment extends V1_30__MoveMetadataToDeploymentBase {

    @Override
    protected void migrateMetadata(Context context) throws Exception {
        log.info("Migrating metadata from image_definition to deployment");
        
        String sql = """
            update deployment d
            set metadata = (
                select metadata from image_definition i
                where i.id = d.image_definition_id
            )
            where d.metadata is null
                    """;
        
        try (var statement = context.getConnection().createStatement()) {
            statement.execute(sql);
        }
    }
    
    @Override
    protected PreparedStatement prepareUpdateStatement(Connection connection) throws Exception {
        return connection.prepareStatement("UPDATE deployment SET metadata = CAST(? AS JSON) WHERE id = ?");
    }
    
    @Override
    protected void setUpdateStatementParams(PreparedStatement statement, String json, UUID id) throws Exception {
        statement.setObject(1, ValueJson.fromJson(json));
        statement.setObject(2, id);
    }
}