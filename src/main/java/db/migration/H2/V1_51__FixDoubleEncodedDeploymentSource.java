package db.migration.H2;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
public class V1_51__FixDoubleEncodedDeploymentSource extends BaseJavaMigration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        log.info("Fixing double-encoded JSON in deployment.source");

        String selectQuery = "SELECT id, CAST(source AS VARCHAR) AS source_text FROM deployment WHERE source IS NOT NULL";
        String updateQuery = "UPDATE deployment SET source = ? FORMAT JSON WHERE id = ?";

        try (Statement selectStmt = context.getConnection().createStatement();
                PreparedStatement updateStmt = context.getConnection().prepareStatement(updateQuery);
                ResultSet rs = selectStmt.executeQuery(selectQuery)) {

            int count = 0;
            while (rs.next()) {
                String id = rs.getString("id");
                String sourceText = rs.getString("source_text");
                String unwrapped = MAPPER.readValue(sourceText, String.class);
                updateStmt.setString(1, unwrapped);
                updateStmt.setString(2, id);
                updateStmt.addBatch();
                count++;
            }

            if (count > 0) {
                updateStmt.executeBatch();
                log.info("Fixed {} double-encoded deployment source entries", count);
            }
        }
    }
}
