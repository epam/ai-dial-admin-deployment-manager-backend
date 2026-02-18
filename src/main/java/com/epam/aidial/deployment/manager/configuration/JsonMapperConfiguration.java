package com.epam.aidial.deployment.manager.configuration;

import com.epam.aidial.deployment.manager.configuration.export.DeploymentExportMixIn;
import com.epam.aidial.deployment.manager.configuration.export.ImageDefinitionExportMixIn;
import com.epam.aidial.deployment.manager.configuration.export.SensitiveEnvVarExportMixIn;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.EnumFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JsonMapperConfiguration {

    @Bean
    @Primary
    public JsonMapper getJsonMapper() {
        return createJsonMapper();
    }

    @Bean
    @Qualifier("prettyJsonMapper")
    public JsonMapper getPrettyJsonMapper() {
        return createPrettyJsonMapper();
    }

    @Bean
    @Qualifier("exportJsonMapper")
    public JsonMapper getExportJsonMapper() {
        JsonMapper mapper = createPrettyJsonMapper();
        mapper.addMixIn(ImageDefinition.class, ImageDefinitionExportMixIn.class);
        mapper.addMixIn(Deployment.class, DeploymentExportMixIn.class);
        mapper.addMixIn(SensitiveEnvVar.class, SensitiveEnvVarExportMixIn.class);
        return mapper;
    }

    public static JsonMapper createJsonMapper() {
        return createDefaultJsonMapperBuilder().build();
    }

    public static JsonMapper createPrettyJsonMapper() {
        return createDefaultJsonMapperBuilder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    private static JsonMapper.Builder createDefaultJsonMapperBuilder() {
        return JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .enable(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .addModule(new JavaTimeModule());
    }

}
