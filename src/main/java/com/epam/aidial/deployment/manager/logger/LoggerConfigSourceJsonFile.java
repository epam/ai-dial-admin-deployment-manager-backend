package com.epam.aidial.deployment.manager.logger;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class LoggerConfigSourceJsonFile implements LoggerConfigSource {

    private final ObjectMapper objectMapper = JsonMapperConfiguration.createJsonMapper();
    private final String loggersPath;

    public LoggerLevelsDto readConfig() {
        var file = new File(loggersPath);
        if (file.exists()) {
            try {
                log.debug("Read configuration from file: {}", file);
                return objectMapper.readValue(file, LoggerLevelsDto.class);
            } catch (JacksonException e) {
                throw new IllegalStateException("Error reading config file: " + loggersPath, e);
            }
        }
        log.debug("Configuration directory {} doesn't exist", file);
        return new LoggerLevelsDto(Map.of());
    }

}
