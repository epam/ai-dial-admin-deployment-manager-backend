package com.epam.aidial.deployment.manager.configuration.datasource;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.JsonAsStringJdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

/**
 * Keeps {@code @JdbcTypeCode(SqlTypes.JSON)} columns mapped to {@code varchar(max)} on SQL Server.
 *
 * <p>Hibernate 7.2.19 added {@code AbstractTransactSQLDialect#contributeTypes}, which registers
 * {@link JsonAsStringJdbcType#NVARCHAR_INSTANCE} for {@code SqlTypes.JSON}. That resolves JSON attributes to
 * {@code nvarchar(max)}, whereas our {@code MS_SQL_SERVER} Flyway migrations create those columns as
 * {@code varchar(max)}. With {@code ddl-auto: validate} the mismatch fails schema validation at startup.
 * Re-registering the non-nationalized descriptor restores the pre-7.2.19 mapping.
 *
 * <p>The trigger is the nationalized descriptor itself, not the dialect: this contributor rewrites the registration
 * only when {@code SqlTypes.JSON} currently resolves to {@link JsonAsStringJdbcType#NVARCHAR_INSTANCE}. POSTGRES
 * ({@code jsonb}) and H2 (native {@code json}) register their own descriptors and are therefore untouched, and no
 * {@code JdbcServices} lookup is needed to tell the vendors apart. If a future Hibernate release maps SQL Server
 * JSON to some third descriptor, this contributor no longer applies and SQL Server startup fails schema validation
 * loudly — deliberately, so the mapping is re-evaluated rather than silently pinned. The SQL Server functional
 * suite catches that in CI.
 *
 * <p>Registered via {@code META-INF/services/org.hibernate.boot.model.TypeContributor}, which is also why the
 * descriptor check matters for correctness of the log line: on the JPA bootstrap path
 * {@code EntityManagerFactoryBuilderImpl#applyTypeContributors} runs the {@link TypeContributor} services once at
 * {@code MetadataBuilder} configuration time — before the dialect contributes — and {@code MetadataBuildingProcess}
 * runs them again afterwards. Only the second pass sees the nationalized descriptor, so only it rewrites anything.
 * The {@code hibernate.type_contributors} property route would never work here: property-supplied contributors run
 * exclusively at the earlier point.
 */
@Slf4j
public class SqlServerJsonAsVarcharTypeContributor implements TypeContributor {

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        JdbcTypeRegistry jdbcTypeRegistry = typeContributions.getTypeConfiguration().getJdbcTypeRegistry();
        if (jdbcTypeRegistry.findDescriptor(SqlTypes.JSON) != JsonAsStringJdbcType.NVARCHAR_INSTANCE) {
            return;
        }

        jdbcTypeRegistry.addDescriptor(JsonAsStringJdbcType.VARCHAR_INSTANCE);

        log.debug("Registered non-nationalized JSON JDBC type for SQL Server; JSON columns map to varchar(max)");
    }
}
