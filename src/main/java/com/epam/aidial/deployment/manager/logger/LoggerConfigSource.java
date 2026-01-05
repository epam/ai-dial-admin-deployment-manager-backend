package com.epam.aidial.deployment.manager.logger;

public interface LoggerConfigSource {
    LoggerLevelsDto readConfig();
}
