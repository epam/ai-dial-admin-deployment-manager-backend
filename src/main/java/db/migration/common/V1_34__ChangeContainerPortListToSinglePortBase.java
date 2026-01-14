package db.migration.common;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

@Slf4j
public abstract class V1_34__ChangeContainerPortListToSinglePortBase extends BaseJavaMigration {

    @Override
    public void migrate(Context context) {
        log.info("Starting migration V1.34: Change container ports list to single port");

        try {
            // Add container_port column to deployment table
            try (Statement statement = context.getConnection().createStatement()) {
                statement.execute(getAddContainerPortSql());
            }
            
            // Add container_grpc_port column to nim_deployment table
            try (Statement statement = context.getConnection().createStatement()) {
                statement.execute(getAddContainerGrpcPortSql());
            }
            
            // Get all deployment records with container_ports
            String selectSql = getSelectDeploymentsSql();
            try (Statement selectStatement = context.getConnection().createStatement();
                    ResultSet resultSet = selectStatement.executeQuery(selectSql)) {
                
                // Prepare update statements
                String updateDeploymentSql = getUpdateDeploymentSql();
                String updateNimDeploymentSql = getUpdateNimDeploymentSql();
                String checkNimSql = getCheckNimDeploymentSql();
                
                try (PreparedStatement updateDeploymentStatement = context.getConnection().prepareStatement(updateDeploymentSql);
                        PreparedStatement updateNimDeploymentStatement = context.getConnection().prepareStatement(updateNimDeploymentSql);
                        PreparedStatement checkNimStatement = context.getConnection().prepareStatement(checkNimSql)) {
                    
                    while (resultSet.next()) {
                        UUID id = resultSet.getObject("id", UUID.class);
                        String containerPorts = resultSet.getString("container_ports");
                        
                        if (containerPorts != null) {
                            String[] ports = containerPorts.split(",");
                            
                            // Extract first port for container_port
                            if (ports.length > 0 && ports[0] != null && !ports[0].equals("null")) {
                                try {
                                    int containerPort = Integer.parseInt(ports[0].trim());
                                    setUpdateDeploymentParams(updateDeploymentStatement, containerPort, id);
                                    updateDeploymentStatement.addBatch();
                                } catch (NumberFormatException e) {
                                    log.warn("Failed to parse container port '{}' for deployment ID: {}", ports[0], id);
                                    throw e;
                                }
                            }
                            
                            // Extract second port for container_grpc_port (only for nim_deployment)
                            if (ports.length > 1 && ports[1] != null && !ports[1].equals("null")) {
                                try {
                                    int grpcPort = Integer.parseInt(ports[1].trim());
                                    
                                    // Check if this is a nim_deployment
                                    checkNimStatement.setObject(1, id);
                                    try (ResultSet nimCheck = checkNimStatement.executeQuery()) {
                                        if (nimCheck.next()) {
                                            setUpdateNimDeploymentParams(updateNimDeploymentStatement, grpcPort, id);
                                            updateNimDeploymentStatement.addBatch();
                                        }
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("Failed to parse grpc port '{}' for deployment ID: {}", ports[1], id);
                                    throw e;
                                }
                            }
                        }
                    }
                    
                    // Execute batch updates
                    updateDeploymentStatement.executeBatch();
                    updateNimDeploymentStatement.executeBatch();
                }
            }
            
            // Drop container_ports column
            try (Statement statement = context.getConnection().createStatement()) {
                statement.execute(getDropContainerPortsSql());
            }
            
            log.info("Successfully completed migration V1.34");
        } catch (Exception e) {
            String message = "Migration V1.34 failed. Reason: " + e.getMessage();
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
    
    protected String getAddContainerPortSql() {
        return "ALTER TABLE deployment ADD COLUMN container_port INT";
    }
    
    protected String getAddContainerGrpcPortSql() {
        return "ALTER TABLE nim_deployment ADD COLUMN container_grpc_port INT";
    }
    
    protected String getSelectDeploymentsSql() {
        return "SELECT id, container_ports FROM deployment WHERE container_ports IS NOT NULL";
    }
    
    protected String getUpdateDeploymentSql() {
        return "UPDATE deployment SET container_port = ? WHERE id = ?";
    }
    
    protected String getUpdateNimDeploymentSql() {
        return "UPDATE nim_deployment SET container_grpc_port = ? WHERE id = ?";
    }
    
    protected String getCheckNimDeploymentSql() {
        return "SELECT 1 FROM nim_deployment WHERE id = ?";
    }
    
    protected String getDropContainerPortsSql() {
        return "ALTER TABLE deployment DROP COLUMN container_ports";
    }
    
    protected void setUpdateDeploymentParams(PreparedStatement statement, int containerPort, UUID id) throws Exception {
        statement.setInt(1, containerPort);
        statement.setObject(2, id);
    }
    
    protected void setUpdateNimDeploymentParams(PreparedStatement statement, int grpcPort, UUID id) throws Exception {
        statement.setInt(1, grpcPort);
        statement.setObject(2, id);
    }
}