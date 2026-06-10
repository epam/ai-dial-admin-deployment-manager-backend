package db.migration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.EnumFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Jackson 2 mapper for Java Flyway migrations, replicating verbatim the
 * {@code JsonMapperConfiguration.createJsonMapper()} the migrations were written against before
 * the application moved to Jackson 3. Applied migrations are historical artifacts — they MUST
 * keep executing the exact (de)serialization logic they shipped with so that databases migrated
 * today are identical to databases migrated before the Jackson upgrade.
 *
 * <p>Do NOT migrate this class or the Java migrations to {@code tools.jackson}, and do not point
 * them back at the application's {@code JsonMapperConfiguration}.
 */
public final class MigrationJsonMapper {

    private MigrationJsonMapper() {
    }

    public static JsonMapper createJsonMapper() {
        return JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .enable(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .addModule(new JavaTimeModule())
                .build();
    }
}
