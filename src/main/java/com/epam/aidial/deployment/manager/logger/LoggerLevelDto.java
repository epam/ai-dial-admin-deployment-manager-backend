package com.epam.aidial.deployment.manager.logger;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoggerLevelDto {
    private String defaultLevel;
    private String configuredLevel;
    private Long validTill;
}
