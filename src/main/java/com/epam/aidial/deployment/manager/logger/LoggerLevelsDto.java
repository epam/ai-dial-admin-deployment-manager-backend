package com.epam.aidial.deployment.manager.logger;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoggerLevelsDto {
    private Map<String, LoggerLevelDto> loggerLevels;
}
