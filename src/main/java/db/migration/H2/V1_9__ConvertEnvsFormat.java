package db.migration.H2;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.h2.value.ValueJson;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;


public class V1_9__ConvertEnvsFormat extends BaseJavaMigration {

    private static final ObjectMapper MAPPER = JsonMapperConfiguration.createJsonMapper();

    @Override
    public void migrate(Context context) throws Exception {
        var select = """
                SELECT id, envs
                  FROM deployment
                  WHERE envs IS NOT NULL
                """;

        try (
                var statement = context.getConnection().createStatement();
                var rs = statement.executeQuery(select);
                var update = context.getConnection()
                        .prepareStatement("UPDATE deployment SET envs = CAST(? AS JSON) WHERE id = ?")
        ) {

            while (rs.next()) {
                var id = rs.getObject("id", UUID.class);
                var json = rs.getString("envs");

                var map = MAPPER.readValue(json,
                        new TypeReference<Map<String, String>>() {
                        });

                var list = new ArrayList<>(map.size());
                for (var e : map.entrySet()) {
                    var n = MAPPER.createObjectNode();
                    n.put("$type", "simple");
                    n.put("name", e.getKey());
                    n.put("value", e.getValue());
                    list.add(n);
                }
                var h2Json = ValueJson.fromJson(MAPPER.writeValueAsString(list));

                update.setObject(1, h2Json);
                update.setObject(2, id);
                update.addBatch();
            }
            update.executeBatch();
        }
    }

}
