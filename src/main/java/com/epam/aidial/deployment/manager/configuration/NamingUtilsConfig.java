package com.epam.aidial.deployment.manager.configuration;

import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NamingUtilsConfig {

    @Value("${app.resource-name-prefix}")
    public void initNamingUtils(String value) {
        K8sNamingUtils.setResourceNamePrefix(value);
    }
}
