package com.epam.aidial.deployment.manager.functional.tests;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class NimRemovalMigrationFunctionalTest {

    private static final String NIM_ID = "nim-legacy-1";
    private static final String MCP_ID = "mcp-keep-1";
    private static final String REUSED_ID = "reused-1";

    /** A fresh, empty database for the vendor under test. */
    protected abstract DataSource freshDataSource() throws SQLException;

    /** Vendor directory name: H2, POSTGRES or MS_SQL_SERVER. */
    protected abstract String vendor();

    /** SQL expression binding a JSON string parameter for this vendor, e.g. {@code CAST(? AS jsonb)}. */
    protected abstract String jsonParam();

    @Test
    void v1_60_shouldRemoveAllNimDataAndKeepOtherDeployments() throws Exception {
        DataSource dataSource = freshDataSource();

        flyway(dataSource, MigrationVersion.fromVersion("1.59")).migrate();
        try (Connection connection = dataSource.getConnection()) {
            seed(connection);
        }

        flyway(dataSource, MigrationVersion.LATEST).migrate();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "deployment")).as("positive control for tableExists").isTrue();
            assertThat(tableExists(connection, "nim_deployment")).isFalse();
            assertThat(tableExists(connection, "nim_deployment_aud")).isFalse();
            assertThat(tableExists(connection, "nim_removed_ids")).isFalse();

            assertThat(count(connection, "SELECT COUNT(*) FROM deployment WHERE id = '" + NIM_ID + "'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_topics WHERE deployment_id = '" + NIM_ID + "'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM component_removal WHERE id = '" + NIM_ID + "'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_aud WHERE id = '" + NIM_ID + "'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_topics_aud WHERE deployment_id = '" + NIM_ID + "'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM audit_activity WHERE resource_type = 'NimDeployment'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM disposable_resource WHERE group_id = '" + NIM_ID + "'"))
                    .as("only the NIM_SERVICE reference is deleted; the deployment's Secret stays for the cleaner")
                    .isEqualTo(1);

            assertThat(count(connection, "SELECT COUNT(*) FROM deployment WHERE id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM mcp_deployment WHERE id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_topics WHERE deployment_id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM component_removal WHERE id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_aud WHERE id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_topics_aud WHERE deployment_id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM audit_activity WHERE resource_type = 'McpDeployment'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM disposable_resource WHERE group_id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM revinfo")).isEqualTo(2);

            // An id reused after a NIM deployment was deleted: only the NIM revision (rev A) is removed, the MCP revision (rev B) survives.
            int revA = count(connection, "SELECT MIN(id) FROM revinfo");
            int revB = count(connection, "SELECT MAX(id) FROM revinfo");
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_aud WHERE id = '" + REUSED_ID + "' AND rev = " + revA)).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_topics_aud WHERE deployment_id = '" + REUSED_ID + "' AND rev = " + revA)).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_aud WHERE id = '" + REUSED_ID + "' AND rev = " + revB)).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM mcp_deployment_aud WHERE id = '" + REUSED_ID + "' AND rev = " + revB)).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_topics_aud WHERE deployment_id = '" + REUSED_ID + "' AND rev = " + revB))
                    .isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment WHERE id = '" + REUSED_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM mcp_deployment WHERE id = '" + REUSED_ID + "'")).isEqualTo(1);
        }
    }

    private Flyway flyway(DataSource dataSource, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/" + vendor())
                .baselineOnMigrate(true)
                .baselineVersion("1.1")
                .target(target)
                .load();
    }

    private void seed(Connection connection) throws SQLException {
        for (String id : new String[] {NIM_ID, MCP_ID, REUSED_ID}) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO deployment (id, display_name, created_at_ms, updated_at_ms, allowed_domains, service_name) "
                            + "VALUES (?, ?, 1, 1, " + jsonParam() + ", ?)")) {
                ps.setString(1, id);
                ps.setString(2, id);
                ps.setString(3, "[]");
                ps.setString(4, id + "-svc");
                ps.executeUpdate();
            }
            execute(connection, "INSERT INTO deployment_topics (deployment_id, topic_name) VALUES ('" + id + "', 'topic')");
            execute(connection, "INSERT INTO component_removal (id, type, created_at) VALUES ('" + id + "', 'DEPLOYMENT', CURRENT_TIMESTAMP)");
        }
        execute(connection, "INSERT INTO nim_deployment (id, container_grpc_port, storage_size) VALUES ('" + NIM_ID + "', 8001, '20Gi')");
        execute(connection, "INSERT INTO mcp_deployment (id, transport) VALUES ('" + MCP_ID + "', 'SSE')");
        execute(connection, "INSERT INTO mcp_deployment (id, transport) VALUES ('" + REUSED_ID + "', 'SSE')");

        execute(connection, "INSERT INTO revinfo (timestamp) VALUES (1)");
        int rev = count(connection, "SELECT MAX(id) FROM revinfo");
        for (String id : new String[] {NIM_ID, MCP_ID}) {
            execute(connection, "INSERT INTO deployment_aud (id, rev, revtype, display_name) VALUES ('" + id + "', " + rev + ", 0, '" + id + "')");
            execute(connection, "INSERT INTO deployment_topics_aud (rev, revtype, deployment_id, topic_name) VALUES (" + rev + ", 0, '" + id + "', 'topic')");
        }
        execute(connection, "INSERT INTO nim_deployment_aud (id, rev) VALUES ('" + NIM_ID + "', " + rev + ")");
        execute(connection, "INSERT INTO mcp_deployment_aud (id, rev) VALUES ('" + MCP_ID + "', " + rev + ")");
        execute(connection, "INSERT INTO audit_activity (activity_id, activity_type, resource_type, resource_id, revision) "
                + "VALUES ('act-nim', 'CREATE', 'NimDeployment', '" + NIM_ID + "', " + rev + ")");
        execute(connection, "INSERT INTO audit_activity (activity_id, activity_type, resource_type, resource_id, revision) "
                + "VALUES ('act-mcp', 'CREATE', 'McpDeployment', '" + MCP_ID + "', " + rev + ")");

        // reused-1: NIM revision at rev (A), later re-created as an MCP deployment at a second revision (B)
        execute(connection, "INSERT INTO deployment_aud (id, rev, revtype, display_name) VALUES ('" + REUSED_ID + "', " + rev + ", 0, '" + REUSED_ID + "')");
        execute(connection, "INSERT INTO deployment_topics_aud (rev, revtype, deployment_id, topic_name) VALUES (" + rev + ", 0, '" + REUSED_ID + "', 'topic')");
        execute(connection, "INSERT INTO nim_deployment_aud (id, rev) VALUES ('" + REUSED_ID + "', " + rev + ")");
        execute(connection, "INSERT INTO revinfo (timestamp) VALUES (2)");
        int revB = count(connection, "SELECT MAX(id) FROM revinfo");
        execute(connection, "INSERT INTO deployment_aud (id, rev, revtype, display_name) VALUES ('" + REUSED_ID + "', " + revB + ", 0, '" + REUSED_ID + "')");
        execute(connection, "INSERT INTO deployment_topics_aud (rev, revtype, deployment_id, topic_name) VALUES (" + revB + ", 0, '" + REUSED_ID + "', 'topic')");
        execute(connection, "INSERT INTO mcp_deployment_aud (id, rev) VALUES ('" + REUSED_ID + "', " + revB + ")");

        insertDisposable(connection, NIM_ID, "NIM_SERVICE", "nim-legacy-svc");
        insertDisposable(connection, NIM_ID, "SECRET", "nim-legacy-secret");
        insertDisposable(connection, MCP_ID, "KNATIVE_SERVICE", "mcp-keep-svc");
    }

    private void insertDisposable(Connection connection, String groupId, String kind, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO disposable_resource (group_id, resource_reference, lifecycle_state, created_at) "
                        + "VALUES (?, " + jsonParam() + ", 'TO_CLEANUP', CURRENT_TIMESTAMP)")) {
            ps.setString(1, groupId);
            ps.setString(2, "{\"$type\":\"k8s\",\"namespace\":\"default\",\"kind\":\"" + kind + "\",\"name\":\"" + name + "\"}");
            ps.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        var metaData = connection.getMetaData();
        for (String candidate : new String[] {table, table.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = metaData.getTables(connection.getCatalog(), null, candidate, new String[] {"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }
}
