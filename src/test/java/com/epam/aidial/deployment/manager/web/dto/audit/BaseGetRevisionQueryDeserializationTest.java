package com.epam.aidial.deployment.manager.web.dto.audit;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseGetRevisionQueryDeserializationTest {

    private final ObjectMapper objectMapper = JsonMapperConfiguration.createJsonMapper();

    @Test
    void deserialize_byTimestampQuery() throws Exception {
        String json = "{\"type\":\"GET_BY_TIMESTAMP\",\"timestamp\":1700000000000}";

        BaseGetRevisionQuery result = objectMapper.readValue(json, BaseGetRevisionQuery.class);

        assertThat(result).isInstanceOf(GetRevisionByTimestampQuery.class);
        GetRevisionByTimestampQuery query = (GetRevisionByTimestampQuery) result;
        assertThat(query.getTimestamp()).isEqualTo(1700000000000L);
    }

    @Test
    void deserialize_byIdQuery() throws Exception {
        String json = "{\"type\":\"GET_BY_ID\",\"id\":42}";

        BaseGetRevisionQuery result = objectMapper.readValue(json, BaseGetRevisionQuery.class);

        assertThat(result).isInstanceOf(GetRevisionByIdQuery.class);
        GetRevisionByIdQuery query = (GetRevisionByIdQuery) result;
        assertThat(query.getId()).isEqualTo(42);
    }

    @Test
    void deserialize_unknownType_throwsException() {
        String json = "{\"type\":\"UNKNOWN\"}";

        assertThatThrownBy(() -> objectMapper.readValue(json, BaseGetRevisionQuery.class))
                .isInstanceOf(InvalidTypeIdException.class);
    }
}
