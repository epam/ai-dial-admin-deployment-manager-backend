package db.migration.MS_SQL_SERVER;

import db.migration.common.V1_34__ChangeContainerPortListToSinglePortBase;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class V1_34__ChangeContainerPortListToSinglePort extends V1_34__ChangeContainerPortListToSinglePortBase {

    @Override
    protected String getAddContainerPortSql() {
        return "ALTER TABLE deployment ADD container_port INT NULL";
    }

    @Override
    protected String getAddContainerGrpcPortSql() {
        return "ALTER TABLE nim_deployment ADD container_grpc_port INT NULL";
    }
}