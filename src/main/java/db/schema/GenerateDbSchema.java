package db.schema;

import org.flywaydb.core.Flyway;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Standalone utility that generates {@code docs/db-schema.md} by running all H2 Flyway
 * migrations against an in-memory database and extracting the resulting schema via JDBC
 * {@link DatabaseMetaData}.
 *
 * <p>This is NOT a Spring component — it is invoked by the {@code generateDbSchema} Gradle task.
 */
public final class GenerateDbSchema {

    private static final String JDBC_URL = "jdbc:h2:mem:schema_gen;DB_CLOSE_DELAY=-1";
    private static final String JDBC_USER = "sa";
    private static final String JDBC_PASSWORD = "";
    private static final Set<String> EXCLUDED_TABLES = Set.of("flyway_schema_history", "shedlock");

    private GenerateDbSchema() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: GenerateDbSchema <output-path>");
            System.exit(1);
        }
        Path outputPath = Path.of(args[0]);

        runMigrations();

        String markdown = generateMarkdown();

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, markdown, StandardCharsets.UTF_8);
        System.out.println("Schema written to " + outputPath);
    }

    private static void runMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, JDBC_USER, JDBC_PASSWORD)
                .locations("classpath:db/migration/H2", "classpath:db/migration/common")
                .baselineOnMigrate(true)
                .baselineVersion("1.1")
                .load();
        flyway.migrate();
    }

    private static String generateMarkdown() throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            DatabaseMetaData meta = conn.getMetaData();
            List<String> tableNames = getTableNames(meta);

            StringBuilder sb = new StringBuilder();
            sb.append("# Database Schema\n\n");
            sb.append("> Auto-generated from H2 Flyway migrations. Do not edit manually.\n");
            sb.append("> Generated at: ").append(Instant.now()).append("\n\n");

            sb.append("## Tables\n\n");
            for (String table : tableNames) {
                sb.append("- [").append(table).append("](#").append(table.toLowerCase()).append(")\n");
            }
            sb.append('\n');

            for (String table : tableNames) {
                appendTableSection(sb, meta, table);
            }

            return sb.toString();
        }
    }

    private static List<String> getTableNames(DatabaseMetaData meta) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(null, "PUBLIC", null, new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (!EXCLUDED_TABLES.contains(name.toLowerCase())) {
                    tables.add(name);
                }
            }
        }
        tables.sort(String.CASE_INSENSITIVE_ORDER);
        return tables;
    }

    private static void appendTableSection(StringBuilder sb, DatabaseMetaData meta, String table) throws SQLException {
        sb.append("## ").append(table).append("\n\n");

        Set<String> pkColumns = getPrimaryKeyColumns(meta, table);
        Map<String, ForeignKey> fkColumns = getForeignKeys(meta, table);

        sb.append("| Column | Type | Nullable | Default | Key |\n");
        sb.append("|--------|------|----------|---------|-----|\n");

        try (ResultSet rs = meta.getColumns(null, "PUBLIC", table, null)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                String nullable = "YES".equals(rs.getString("IS_NULLABLE")) ? "Yes" : "No";
                String defaultVal = rs.getString("COLUMN_DEF");
                if (defaultVal == null) {
                    defaultVal = "";
                }

                String key = "";
                if (pkColumns.contains(col)) {
                    key = "PK";
                }
                if (fkColumns.containsKey(col)) {
                    ForeignKey fk = fkColumns.get(col);
                    key += (key.isEmpty() ? "" : ", ") + "FK → " + fk.refTable + "." + fk.refColumn;
                }

                String typeStr = formatType(type, size);
                sb.append("| ").append(col)
                        .append(" | ").append(typeStr)
                        .append(" | ").append(nullable)
                        .append(" | ").append(escapeMarkdown(defaultVal))
                        .append(" | ").append(key)
                        .append(" |\n");
            }
        }

        List<IndexInfo> indexes = getIndexes(meta, table);
        if (!indexes.isEmpty()) {
            sb.append("\n**Indexes:**\n\n");
            for (IndexInfo idx : indexes) {
                String unique = idx.unique ? "UNIQUE " : "";
                sb.append("- `").append(unique).append(idx.name).append("` on (")
                        .append(String.join(", ", idx.columns)).append(")\n");
            }
        }

        sb.append('\n');
    }

    private static Set<String> getPrimaryKeyColumns(DatabaseMetaData meta, String table) throws SQLException {
        Set<String> pks = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (ResultSet rs = meta.getPrimaryKeys(null, "PUBLIC", table)) {
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pks;
    }

    private static Map<String, ForeignKey> getForeignKeys(DatabaseMetaData meta, String table) throws SQLException {
        Map<String, ForeignKey> fks = new LinkedHashMap<>();
        try (ResultSet rs = meta.getImportedKeys(null, "PUBLIC", table)) {
            while (rs.next()) {
                String col = rs.getString("FKCOLUMN_NAME");
                String refTable = rs.getString("PKTABLE_NAME");
                String refCol = rs.getString("PKCOLUMN_NAME");
                fks.put(col, new ForeignKey(refTable, refCol));
            }
        }
        return fks;
    }

    private static List<IndexInfo> getIndexes(DatabaseMetaData meta, String table) throws SQLException {
        Map<String, IndexInfo> indexMap = new LinkedHashMap<>();
        try (ResultSet rs = meta.getIndexInfo(null, "PUBLIC", table, false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null || indexName.startsWith("PRIMARY_KEY")) {
                    continue;
                }
                String col = rs.getString("COLUMN_NAME");
                if (col == null) {
                    continue;
                }
                boolean unique = !rs.getBoolean("NON_UNIQUE");
                indexMap.computeIfAbsent(indexName, k -> new IndexInfo(k, unique))
                        .columns.add(col);
            }
        }
        return new ArrayList<>(indexMap.values());
    }

    private static String formatType(String type, int size) {
        return switch (type) {
            case "CHARACTER VARYING" -> "VARCHAR(" + size + ")";
            case "CHARACTER LARGE OBJECT" -> "CLOB";
            case "BINARY LARGE OBJECT" -> "BLOB";
            default -> type;
        };
    }

    private static String escapeMarkdown(String value) {
        return value.replace("|", "\\|");
    }

    private record ForeignKey(String refTable, String refColumn) { }

    private static final class IndexInfo {
        final String name;
        final boolean unique;
        final List<String> columns = new ArrayList<>();

        IndexInfo(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }
    }
}
