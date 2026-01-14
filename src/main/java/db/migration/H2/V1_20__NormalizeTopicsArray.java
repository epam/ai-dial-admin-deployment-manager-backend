package db.migration.H2;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class V1_20__NormalizeTopicsArray extends BaseJavaMigration {

    @Override
    public void migrate(Context context) {
        log.info("Starting migration V1.20...");
        var select = """
                SELECT id, topics
                  FROM image_definition
                  WHERE topics IS NOT NULL
                """;

        log.debug("Executing topics normalization select for 'image_definition'");
        try (
                var statement = context.getConnection().createStatement();
                var rs = statement.executeQuery(select);
                var insert = context.getConnection().prepareStatement(
                        "INSERT INTO image_definition_topics (image_definition_id, topic_name) VALUES (?, ?)")
        ) {
            while (rs.next()) {
                var imageDefId = rs.getObject("id", UUID.class);
                var topicsArray = rs.getArray("topics");

                if (topicsArray != null) {
                    var topics = Arrays.stream((Object[]) topicsArray.getArray())
                            .map(val -> (String) val)
                            .collect(Collectors.toSet());
                    log.debug("Migrating row with ID '%s'. topics: %s".formatted(imageDefId, topics));

                    // Insert each topic
                    for (String topicName : topics) {
                        insert.setObject(1, imageDefId);
                        insert.setString(2, topicName);
                        insert.addBatch();
                    }
                }
            }
            insert.executeBatch();
            log.info("Successfully completed migration V1.20");
        } catch (Exception e) {
            String message = "Migration 1.20 failed. Reason: " + e.getMessage();
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

}