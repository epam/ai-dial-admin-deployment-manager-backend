package com.epam.aidial.deployment.manager.web.dto.audit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GetRevisionByTimestampQuery extends BaseGetRevisionQuery {

    @NotNull
    @Positive
    private Long timestamp;
}
