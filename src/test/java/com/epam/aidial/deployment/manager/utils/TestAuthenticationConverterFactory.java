package com.epam.aidial.deployment.manager.utils;

import com.epam.aidial.deployment.manager.web.security.JwtAuthenticationConverterFactory;
import com.epam.aidial.deployment.manager.web.security.JwtProviderUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

@Configuration
public class TestAuthenticationConverterFactory {

    @Primary
    @Bean
    public JwtAuthenticationConverterFactory getAuthenticationConverterFactory() {
        return createJwtAuthenticationConverterFactory();
    }

    public static JwtAuthenticationConverterFactory createJwtAuthenticationConverterFactory() {
        var config = JwtProviderTestHelper.createProviderConfig();
        return new JwtAuthenticationConverterFactory(
                Map.of(config.getIssuer(), config),
                new JwtProviderUtils()
        );
    }
}