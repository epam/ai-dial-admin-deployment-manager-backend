package com.epam.aidial.deployment.manager.docker.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@JsonIgnoreProperties(
        ignoreUnknown = true
)
public class ContainerConfigurationTemplateDto {

    private ConfigurationObjectTemplate config = new ConfigurationObjectTemplate();

    @Data
    public static class ConfigurationObjectTemplate {

        @Nullable
        @JsonAlias("Entrypoint")
        private List<String> entrypoint;

        @Nullable
        @JsonAlias("Cmd")
        private List<String> cmd;
    }

}
