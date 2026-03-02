package com.epam.aidial.deployment.manager.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.config.export")
public class ConfigExportProperties {
    private String fileName;
    private String zipName;
}
