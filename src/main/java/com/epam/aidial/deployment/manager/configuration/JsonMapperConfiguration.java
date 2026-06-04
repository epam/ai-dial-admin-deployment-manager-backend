package com.epam.aidial.deployment.manager.configuration;

import com.epam.aidial.deployment.manager.configuration.export.DeploymentExportMixIn;
import com.epam.aidial.deployment.manager.configuration.export.ImageDefinitionExportMixIn;
import com.epam.aidial.deployment.manager.configuration.export.InternalImageSourceExportMixIn;
import com.epam.aidial.deployment.manager.configuration.export.SensitiveEnvVarExportMixIn;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

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
        // Jackson 3 mappers are immutable, so mix-ins must be registered on the builder
        return createPrettyJsonMapperBuilder()
                .addMixIn(ImageDefinition.class, ImageDefinitionExportMixIn.class)
                .addMixIn(Deployment.class, DeploymentExportMixIn.class)
                .addMixIn(InternalImageSource.class, InternalImageSourceExportMixIn.class)
                .addMixIn(SensitiveEnvVar.class, SensitiveEnvVarExportMixIn.class)
                .build();
    }

    public static JsonMapper createJsonMapper() {
        return createDefaultJsonMapperBuilder().build();
    }

    public static JsonMapper createPrettyJsonMapper() {
        return createPrettyJsonMapperBuilder().build();
    }

    private static JsonMapper.Builder createPrettyJsonMapperBuilder() {
        return createDefaultJsonMapperBuilder()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    private static JsonMapper.Builder createDefaultJsonMapperBuilder() {
        return JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .enable(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
                // preserve Jackson 2 enum semantics: (de)serialize via name(), not toString() —
                // Jackson 3 flipped both defaults to enabled, which would silently change the wire
                // format for any future enum that overrides toString()
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
                .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
                // preserve the Jackson 2 wire format: dates as millisecond timestamps, declaration-ordered properties
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                // preserve Jackson 2 creator semantics: deserialize POJOs via setters/field defaults,
                // not constructor parameters (Jackson 3 detects parameter names by default,
                // which bypasses Lombok @Builder.Default field initializers)
                .disable(MapperFeature.DETECT_PARAMETER_NAMES)
                .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // preserve Jackson 2 deserialization leniency — Jackson 3 flipped both defaults to enabled:
                // an explicit JSON null for a primitive DTO field keeps coercing to 0/false instead of failing,
                // and content trailing a JSON document keeps being ignored instead of being rejected
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .configure(StreamReadFeature.AUTO_CLOSE_SOURCE, false)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(new KubernetesModelJacksonModule());
    }

}
