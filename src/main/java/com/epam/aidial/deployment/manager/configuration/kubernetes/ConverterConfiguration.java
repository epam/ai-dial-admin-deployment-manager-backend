package com.epam.aidial.deployment.manager.configuration.kubernetes;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConverterConfiguration {

    @Bean
    @ConfigurationPropertiesBinding
    public static StringToIntOrStringConverter stringToIntOrStringConverter() {
        return new StringToIntOrStringConverter();
    }

    @Bean
    @ConfigurationPropertiesBinding
    public static IntegerToIntOrStringConverter integerToIntOrStringConverter() {
        return new IntegerToIntOrStringConverter();
    }

}
