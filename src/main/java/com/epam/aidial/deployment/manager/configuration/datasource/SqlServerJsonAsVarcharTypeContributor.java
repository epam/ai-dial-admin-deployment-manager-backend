package com.epam.aidial.deployment.manager.configuration.datasource;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.jdbc.JsonAsStringJdbcType;

/**
 * Keeps {@code @JdbcTypeCode(SqlTypes.JSON)} columns mapped to {@code varchar(max)} on SQL Server.
 *
 * <p>Hibernate 7.2.19 added {@code AbstractTransactSQLDialect#contributeTypes}, which registers
 * {@link JsonAsStringJdbcType#NVARCHAR_INSTANCE} for {@code SqlTypes.JSON}. That resolves JSON attributes to
 * {@code nvarchar(max)}, whereas our {@code MS_SQL_SERVER} Flyway migrations create those 26 columns as
 * {@code varchar(max)}. With {@code ddl-auto: validate} the mismatch fails schema validation at startup.
 *
 * <p>Re-registering the non-nationalized descriptor restores the pre-7.2.19 mapping. Dialect contributions are
 * applied before {@link TypeContributor} services (see {@code MetadataBuildingProcess}), so this registration wins.
 * Note that the {@code hibernate.type_contributors} property route would NOT work here: those contributors run at
 * {@code MetadataBuilder} configuration time, i.e. before the dialect overwrites the JSON registration.
 *
 * <p>Registered via {@code META-INF/services/org.hibernate.boot.model.TypeContributor}. Because that file is
 * global, the dialect is checked explicitly — POSTGRES ({@code jsonb}) and H2 (native {@code json}) map JSON
 * independently of nationalization and must keep the stock behaviour.
 */
@Slf4j
public class SqlServerJsonAsVarcharTypeContributor implements TypeContributor {

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        if (!(serviceRegistry.requireService(JdbcServices.class).getDialect() instanceof SQLServerDialect)) {
            return;
        }

        typeContributions.getTypeConfiguration()
                .getJdbcTypeRegistry()
                .addDescriptor(JsonAsStringJdbcType.VARCHAR_INSTANCE);

        log.info("Registered non-nationalized JSON JDBC type for SQL Server; JSON columns map to varchar(max)");
    }
}
