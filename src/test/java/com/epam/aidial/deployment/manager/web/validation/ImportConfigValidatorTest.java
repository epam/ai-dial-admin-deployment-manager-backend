package com.epam.aidial.deployment.manager.web.validation;

import com.epam.aidial.deployment.manager.exception.ImportValidationException;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.web.dto.DeploymentMetadataDto;
import com.epam.aidial.deployment.manager.web.dto.DockerImageSourceDto;
import com.epam.aidial.deployment.manager.web.dto.ImageBuilderDto;
import com.epam.aidial.deployment.manager.web.dto.ImageTypeDto;
import com.epam.aidial.deployment.manager.web.dto.McpImageDefinitionRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateInternalImageDeploymentSourceRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateMcpDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.mapper.DeploymentDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionDtoMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportConfigValidatorTest {

    @Mock
    private DeploymentDtoMapper deploymentDtoMapper;
    @Mock
    private ImageDefinitionDtoMapper imageDefinitionDtoMapper;

    private ImportConfigValidator importConfigValidator;
    private ExportConfig validConfig;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        importConfigValidator = new ImportConfigValidator(validator, deploymentDtoMapper, imageDefinitionDtoMapper);
        validConfig = ExportConfig.builder().build();
    }

    @Test
    void shouldPassValidation_whenConfigIsEmpty() {
        var errors = importConfigValidator.collectErrors(validConfig);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldPassValidation_whenAllEntitiesValid() {
        var dep = buildMcpDeployment("valid-dep");
        validConfig.setMcpDeployments(Map.of("valid-dep", dep));
        when(deploymentDtoMapper.toCreateDeploymentRequestDto(any())).thenReturn(buildValidDeploymentDto("valid-dep"));

        var imgDef = buildMcpImageDefinition("valid-img");
        validConfig.setMcpImageDefinitions(Map.of("valid-img", imgDef));
        when(imageDefinitionDtoMapper.toImageDefinitionRequestDto(any())).thenReturn(buildValidImageDefDto("valid-img"));

        var errors = importConfigValidator.collectErrors(validConfig);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldFailValidation_whenDeploymentNameInvalid() {
        var dep = buildMcpDeployment("INVALID-UPPERCASE");
        validConfig.setMcpDeployments(Map.of("INVALID-UPPERCASE", dep));
        var dto = buildValidDeploymentDto("INVALID-UPPERCASE");
        when(deploymentDtoMapper.toCreateDeploymentRequestDto(any())).thenReturn(dto);

        var errors = importConfigValidator.collectErrors(validConfig);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> "MCP_DEPLOYMENT".equals(e.entityType()) && "name".equals(e.fieldPath()));
    }

    @Test
    void shouldFailValidation_whenDeploymentMissingRequiredField() {
        var dep = buildMcpDeployment("valid-id");
        validConfig.setMcpDeployments(Map.of("valid-id", dep));
        var dto = buildValidDeploymentDto("valid-id");
        dto.setDisplayName(null);
        when(deploymentDtoMapper.toCreateDeploymentRequestDto(any())).thenReturn(dto);

        var errors = importConfigValidator.collectErrors(validConfig);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> "displayName".equals(e.fieldPath()));
    }

    @Test
    void shouldFailValidation_whenDeploymentDisplayNameTooLong() {
        var dep = buildMcpDeployment("valid-id");
        validConfig.setMcpDeployments(Map.of("valid-id", dep));
        var dto = buildValidDeploymentDto("valid-id");
        dto.setDisplayName("x".repeat(256));
        when(deploymentDtoMapper.toCreateDeploymentRequestDto(any())).thenReturn(dto);

        var errors = importConfigValidator.collectErrors(validConfig);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> "displayName".equals(e.fieldPath()));
    }

    @Test
    void shouldFailValidation_whenImageDefinitionVersionInvalid() {
        var imgDef = buildMcpImageDefinition("valid-img");
        validConfig.setMcpImageDefinitions(Map.of("valid-img", imgDef));
        var dto = buildValidImageDefDto("valid-img");
        dto.setVersion("not-a-version");
        when(imageDefinitionDtoMapper.toImageDefinitionRequestDto(any())).thenReturn(dto);

        var errors = importConfigValidator.collectErrors(validConfig);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> "MCP_IMAGE_DEFINITION".equals(e.entityType()) && "version".equals(e.fieldPath()));
    }

    @Test
    void shouldFailValidation_whenImageDefinitionMissingRequiredField() {
        var imgDef = buildMcpImageDefinition("valid-img");
        validConfig.setMcpImageDefinitions(Map.of("valid-img", imgDef));
        var dto = buildValidImageDefDto("valid-img");
        dto.setVersion(null);
        when(imageDefinitionDtoMapper.toImageDefinitionRequestDto(any())).thenReturn(dto);

        var errors = importConfigValidator.collectErrors(validConfig);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> "version".equals(e.fieldPath()));
    }

    @Test
    void shouldCollectAllViolations_whenMultipleEntitiesInvalid() {
        var dep1 = buildMcpDeployment("BAD-1");
        var dep2 = buildMcpDeployment("BAD-2");
        var deployments = new LinkedHashMap<String, McpDeployment>();
        deployments.put("BAD-1", dep1);
        deployments.put("BAD-2", dep2);
        validConfig.setMcpDeployments(deployments);

        when(deploymentDtoMapper.toCreateDeploymentRequestDto(dep1)).thenReturn(buildValidDeploymentDto("BAD-1"));
        when(deploymentDtoMapper.toCreateDeploymentRequestDto(dep2)).thenReturn(buildValidDeploymentDto("BAD-2"));

        var imgDef = buildMcpImageDefinition("valid-img");
        validConfig.setMcpImageDefinitions(Map.of("valid-img", imgDef));
        var imgDto = buildValidImageDefDto("valid-img");
        imgDto.setVersion("bad");
        when(imageDefinitionDtoMapper.toImageDefinitionRequestDto(any())).thenReturn(imgDto);

        var errors = importConfigValidator.collectErrors(validConfig);
        assertThat(errors.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void shouldThrowImportValidationException_whenValidateFails() {
        var dep = buildMcpDeployment("INVALID");
        validConfig.setMcpDeployments(Map.of("INVALID", dep));
        when(deploymentDtoMapper.toCreateDeploymentRequestDto(any())).thenReturn(buildValidDeploymentDto("INVALID"));

        assertThatThrownBy(() -> importConfigValidator.validate(validConfig))
                .isInstanceOf(ImportValidationException.class)
                .satisfies(ex -> {
                    var errors = ((ImportValidationException) ex).getErrors();
                    assertThat(errors).isNotEmpty();
                });
    }

    @Test
    void shouldFailValidation_whenGlobalDomainWhitelistInvalid() {
        validConfig.setGlobalImageBuildDomainWhitelist(List.of("not a valid domain!!!"));

        var errors = importConfigValidator.collectErrors(validConfig);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> "GLOBAL_DOMAIN_WHITELIST".equals(e.entityType()));
    }

    @Test
    void shouldPassValidation_whenGlobalDomainWhitelistValid() {
        validConfig.setGlobalImageBuildDomainWhitelist(List.of("example.com", "sub.domain.org"));

        var errors = importConfigValidator.collectErrors(validConfig);
        assertThat(errors.stream().filter(e -> "GLOBAL_DOMAIN_WHITELIST".equals(e.entityType())).toList()).isEmpty();
    }

    private McpDeployment buildMcpDeployment(String id) {
        var dep = new McpDeployment();
        dep.setId(id);
        dep.setDisplayName("Valid Display Name");
        dep.setMetadata(new DeploymentMetadata());
        dep.setSource(new InternalImageSource(null, null, "img-name", "1.0.0"));
        return dep;
    }

    private McpImageDefinition buildMcpImageDefinition(String name) {
        var imgDef = new McpImageDefinition();
        imgDef.setName(name);
        imgDef.setVersion("1.0.0");
        return imgDef;
    }

    private CreateMcpDeploymentRequestDto buildValidDeploymentDto(String name) {
        var dto = new CreateMcpDeploymentRequestDto();
        dto.setName(name);
        dto.setDisplayName("Valid Display Name");
        dto.setMetadata(new DeploymentMetadataDto(List.of()));
        dto.setSource(new CreateInternalImageDeploymentSourceRequestDto(
                null, ImageTypeDto.MCP, "img-name", "1.0.0"));
        return dto;
    }

    private McpImageDefinitionRequestDto buildValidImageDefDto(String name) {
        var dto = new McpImageDefinitionRequestDto();
        dto.setName(name);
        dto.setVersion("1.0.0");
        dto.setSource(new DockerImageSourceDto("docker.io/test:latest", null, null));
        dto.setImageBuilder(ImageBuilderDto.BUILDKIT);
        return dto;
    }
}
