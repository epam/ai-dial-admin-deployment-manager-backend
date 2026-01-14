package com.epam.aidial.deployment.manager.logger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "logger.configuration")
public class LoggerConfigProperties {
    private String path;
}
