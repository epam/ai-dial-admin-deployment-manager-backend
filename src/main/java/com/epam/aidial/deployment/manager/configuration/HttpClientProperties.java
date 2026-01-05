package com.epam.aidial.deployment.manager.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "http.client")
@Getter
@Setter
public class HttpClientProperties {
    /**
     * Connect timeout in milliseconds.
     */
    private int connectTimeout = 30000;

    /**
     * Read timeout in milliseconds.
     */
    private int readTimeout = 60000;

    /**
     * Write timeout in milliseconds.
     */
    private int writeTimeout = 60000;
}
