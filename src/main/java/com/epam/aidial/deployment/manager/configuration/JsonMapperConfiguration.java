package com.epam.aidial.deployment.manager.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.EnumFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonMapperConfiguration {

    @Bean
    public JsonMapper getJsonMapper() {
        return createJsonMapper();
    }

    public static JsonMapper createJsonMapper() {
        return JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .enable(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .addModule(new JavaTimeModule())
                .build();
    }

}
