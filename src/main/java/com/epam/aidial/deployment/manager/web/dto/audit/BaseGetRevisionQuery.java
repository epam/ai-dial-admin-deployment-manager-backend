package com.epam.aidial.deployment.manager.web.dto.audit;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = GetRevisionByTimestampQuery.class, name = "GET_BY_TIMESTAMP"),
        @JsonSubTypes.Type(value = GetRevisionByIdQuery.class, name = "GET_BY_ID")
})
@Data
public abstract class BaseGetRevisionQuery {
}
