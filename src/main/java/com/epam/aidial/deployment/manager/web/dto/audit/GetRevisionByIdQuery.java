package com.epam.aidial.deployment.manager.web.dto.audit;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GetRevisionByIdQuery extends BaseGetRevisionQuery {

    @NotNull
    private Integer id;
}
