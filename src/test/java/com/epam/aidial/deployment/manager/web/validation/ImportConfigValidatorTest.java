package com.epam.aidial.deployment.manager.web.validation;

import com.epam.aidial.deployment.manager.exception.ImportValidationException;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ImportValidationError;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        importConfigValidator = new ImportConfigValidator(validator, deploymentDtoMapper, imageDefinitionDtoMapper);
    }

    @Nested
    class EmptyConfig {

        @Test
        void shouldPassValidation_whenConfigIsEmpty() {
            var errors = importConfigValidator.collectErrors(ExportConfig.builder().build());
            assertThat(errors).isEmpty();
        }

        @Test
        void shouldPassValidation_whenAllMapsAreNull() {
            var config = new ExportConfig();
            config.setMcpDeployments(null);
            config.setMcpImageDefinitions(null);
            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).isEmpty();
        }
    }

    @Nested
    class ValidEntities {

        @Test
        void shouldPassValidation_whenDeploymentIsValid() {
            var config = configWithDeployment("valid-dep", buildValidDeploymentDto("valid-dep"));
            assertThat(importConfigValidator.collectErrors(config)).isEmpty();
        }

        @Test
        void shouldPassValidation_whenImageDefinitionIsValid() {
            var config = configWithImageDef("valid-img", buildValidImageDefDto("valid-img"));
            assertThat(importConfigValidator.collectErrors(config)).isEmpty();
        }

        @Test
        void shouldNotThrow_whenValidateCalledOnValidConfig() {
            var config = configWithDeployment("valid-dep", buildValidDeploymentDto("valid-dep"));
            assertThatCode(() -> importConfigValidator.validate(config)).doesNotThrowAnyException();
        }
    }

    @Nested
    class DeploymentValidation {

        @Test
        void shouldFailValidation_whenNameContainsUppercase() {
            var config = configWithDeployment("BAD-NAME", buildValidDeploymentDto("BAD-NAME"));

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors)
                    .hasSize(1)
                    .first()
                    .satisfies(e -> {
                        assertThat(e.entityType()).isEqualTo("MCP_DEPLOYMENT");
                        assertThat(e.entityIdentifier()).isEqualTo("BAD-NAME");
                        assertThat(e.fieldPath()).isEqualTo("name");
                    });
        }

        @Test
        void shouldFailValidation_whenNameTooShort() {
            var config = configWithDeployment("x", buildValidDeploymentDto("x"));

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).isNotEmpty();
            assertThat(errors).anyMatch(e -> "name".equals(e.fieldPath()));
        }

        @Test
        void shouldFailValidation_whenDisplayNameIsNull() {
            var dto = buildValidDeploymentDto("valid-id");
            dto.setDisplayName(null);
            var config = configWithDeployment("valid-id", dto);

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).isNotEmpty();
            assertThat(errors).anyMatch(e -> "displayName".equals(e.fieldPath()));
        }

        @Test
        void shouldFailValidation_whenDisplayNameTooLong() {
            var dto = buildValidDeploymentDto("valid-id");
            dto.setDisplayName("x".repeat(256));
            var config = configWithDeployment("valid-id", dto);

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).isNotEmpty();
            assertThat(errors).anyMatch(e -> "displayName".equals(e.fieldPath()));
        }

        @Test
        void shouldFailValidation_whenMetadataIsNull() {
            var dto = buildValidDeploymentDto("valid-id");
            dto.setMetadata(null);
            var config = configWithDeployment("valid-id", dto);

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).isNotEmpty();
            assertThat(errors).anyMatch(e -> "metadata".equals(e.fieldPath()));
        }
    }

    @Nested
    class ImageDefinitionValidation {

        @Test
        void shouldFailValidation_whenVersionIsInvalid() {
            var dto = buildValidImageDefDto("valid-img");
            dto.setVersion("not-semver");
            var config = configWithImageDef("valid-img", dto);

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors)
                    .hasSize(1)
                    .first()
                    .satisfies(e -> {
                        assertThat(e.entityType()).isEqualTo("MCP_IMAGE_DEFINITION");
                        assertThat(e.entityIdentifier()).isEqualTo("valid-img");
                        assertThat(e.fieldPath()).isEqualTo("version");
                    });
        }

        @Test
        void shouldFailValidation_whenVersionIsNull() {
            var dto = buildValidImageDefDto("valid-img");
            dto.setVersion(null);
            var config = configWithImageDef("valid-img", dto);

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).isNotEmpty();
            assertThat(errors).anyMatch(e -> "version".equals(e.fieldPath()));
        }

        @Test
        void shouldFailValidation_whenSourceIsNull() {
            var dto = buildValidImageDefDto("valid-img");
            dto.setSource(null);
            var config = configWithImageDef("valid-img", dto);

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).isNotEmpty();
            assertThat(errors).anyMatch(e -> "source".equals(e.fieldPath()));
        }
    }

    @Nested
    class MultipleViolations {

        @Test
        void shouldCollectViolationsFromMultipleDeployments() {
            var dep1 = buildDomainDeployment("BAD-1");
            var dep2 = buildDomainDeployment("BAD-2");
            var deployments = new LinkedHashMap<String, McpDeployment>();
            deployments.put("BAD-1", dep1);
            deployments.put("BAD-2", dep2);

            when(deploymentDtoMapper.toCreateDeploymentRequestDto(dep1)).thenReturn(buildValidDeploymentDto("BAD-1"));
            when(deploymentDtoMapper.toCreateDeploymentRequestDto(dep2)).thenReturn(buildValidDeploymentDto("BAD-2"));

            var config = ExportConfig.builder().build();
            config.setMcpDeployments(deployments);

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).hasSizeGreaterThanOrEqualTo(2);
            assertThat(errors).extracting(ImportValidationError::entityIdentifier)
                    .contains("BAD-1", "BAD-2");
        }

        @Test
        void shouldCollectViolationsAcrossDeploymentsAndImageDefinitions() {
            var config = ExportConfig.builder().build();

            config.setMcpDeployments(Map.of("BAD-DEP", buildDomainDeployment("BAD-DEP")));
            when(deploymentDtoMapper.toCreateDeploymentRequestDto(any())).thenReturn(buildValidDeploymentDto("BAD-DEP"));

            var imgDto = buildValidImageDefDto("bad-img");
            imgDto.setVersion("invalid");
            config.setMcpImageDefinitions(Map.of("bad-img", buildDomainImageDef("bad-img")));
            when(imageDefinitionDtoMapper.toImageDefinitionRequestDto(any())).thenReturn(imgDto);

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).hasSizeGreaterThanOrEqualTo(2);
            assertThat(errors).extracting(ImportValidationError::entityType)
                    .contains("MCP_DEPLOYMENT", "MCP_IMAGE_DEFINITION");
        }

        @Test
        void shouldCollectMultipleViolationsFromSingleEntity() {
            var dto = buildValidDeploymentDto("BAD");
            dto.setDisplayName(null); // @NotNull violation
            // name "BAD" also fails @Pattern
            var config = configWithDeployment("BAD", dto);

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).hasSizeGreaterThanOrEqualTo(2);
            assertThat(errors).extracting(ImportValidationError::fieldPath)
                    .contains("name", "displayName");
        }
    }

    @Nested
    class MapperFailure {

        @Test
        void shouldReportMappingError_whenDeploymentMapperThrows() {
            var config = ExportConfig.builder().build();
            config.setMcpDeployments(Map.of("broken", buildDomainDeployment("broken")));
            when(deploymentDtoMapper.toCreateDeploymentRequestDto(any()))
                    .thenThrow(new IllegalArgumentException("unsupported source type"));

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors)
                    .hasSize(1)
                    .first()
                    .satisfies(e -> {
                        assertThat(e.entityType()).isEqualTo("MCP_DEPLOYMENT");
                        assertThat(e.entityIdentifier()).isEqualTo("broken");
                        assertThat(e.fieldPath()).isEmpty();
                        assertThat(e.message()).contains("Mapping failed");
                    });
        }

        @Test
        void shouldReportMappingError_whenImageDefMapperThrows() {
            var config = ExportConfig.builder().build();
            config.setMcpImageDefinitions(Map.of("broken", buildDomainImageDef("broken")));
            when(imageDefinitionDtoMapper.toImageDefinitionRequestDto(any()))
                    .thenThrow(new IllegalArgumentException("unsupported source"));

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors)
                    .hasSize(1)
                    .first()
                    .satisfies(e -> {
                        assertThat(e.entityType()).isEqualTo("MCP_IMAGE_DEFINITION");
                        assertThat(e.entityIdentifier()).isEqualTo("broken");
                        assertThat(e.message()).contains("Mapping failed");
                    });
        }
    }

    @Nested
    class GlobalDomainWhitelist {

        @Test
        void shouldPassValidation_whenWhitelistIsEmpty() {
            var config = ExportConfig.builder().globalImageBuildDomainWhitelist(new ArrayList<>()).build();
            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).filteredOn(e -> "GLOBAL_DOMAIN_WHITELIST".equals(e.entityType())).isEmpty();
        }

        @Test
        void shouldPassValidation_whenWhitelistContainsValidDomains() {
            var config = ExportConfig.builder()
                    .globalImageBuildDomainWhitelist(List.of("example.com", "sub.domain.org"))
                    .build();
            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).filteredOn(e -> "GLOBAL_DOMAIN_WHITELIST".equals(e.entityType())).isEmpty();
        }

        @Test
        void shouldPassValidation_whenWhitelistContainsWildcard() {
            var config = ExportConfig.builder()
                    .globalImageBuildDomainWhitelist(List.of("*"))
                    .build();
            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).filteredOn(e -> "GLOBAL_DOMAIN_WHITELIST".equals(e.entityType())).isEmpty();
        }

        @Test
        void shouldFailValidation_whenWhitelistContainsInvalidDomain() {
            var config = ExportConfig.builder()
                    .globalImageBuildDomainWhitelist(List.of("not a valid domain!!!"))
                    .build();

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors)
                    .filteredOn(e -> "GLOBAL_DOMAIN_WHITELIST".equals(e.entityType()))
                    .hasSize(1)
                    .first()
                    .satisfies(e -> assertThat(e.entityIdentifier()).isEqualTo("not a valid domain!!!"))
                    .satisfies(e -> assertThat(e.message()).contains("not a valid domain!!!"));
        }

        @Test
        void shouldReportEachInvalidDomainSeparately() {
            var config = ExportConfig.builder()
                    .globalImageBuildDomainWhitelist(List.of("valid.com", "bad!", "also bad!!"))
                    .build();

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors).filteredOn(e -> "GLOBAL_DOMAIN_WHITELIST".equals(e.entityType()))
                    .hasSize(2)
                    .extracting(ImportValidationError::entityIdentifier)
                    .containsExactlyInAnyOrder("bad!", "also bad!!");
        }

        @Test
        void shouldKeyErrorByOffendingDomain() {
            var config = ExportConfig.builder()
                    .globalImageBuildDomainWhitelist(List.of("valid.com", "bad!"))
                    .build();

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors)
                    .filteredOn(e -> "GLOBAL_DOMAIN_WHITELIST".equals(e.entityType()))
                    .hasSize(1)
                    .first()
                    .satisfies(e -> assertThat(e.entityIdentifier()).isEqualTo("bad!"))
                    .satisfies(e -> assertThat(e.fieldPath()).isEqualTo("globalImageBuildDomainWhitelist"));
        }

        @Test
        void shouldFailValidation_whenWhitelistContainsNullDomain() {
            var domains = new ArrayList<String>();
            domains.add(null);
            var config = ExportConfig.builder().globalImageBuildDomainWhitelist(domains).build();

            var errors = importConfigValidator.collectErrors(config);
            assertThat(errors)
                    .filteredOn(e -> "GLOBAL_DOMAIN_WHITELIST".equals(e.entityType()))
                    .hasSize(1)
                    .first()
                    .satisfies(e -> assertThat(e.message()).contains("must not be null"));
        }
    }

    @Nested
    class ValidateMethod {

        @Test
        void shouldThrowImportValidationException_withAllErrors() {
            var config = configWithDeployment("INVALID", buildValidDeploymentDto("INVALID"));

            assertThatThrownBy(() -> importConfigValidator.validate(config))
                    .isInstanceOf(ImportValidationException.class)
                    .satisfies(ex -> {
                        var errors = ((ImportValidationException) ex).getErrors();
                        assertThat(errors).isNotEmpty();
                        assertThat(errors).allMatch(e -> "MCP_DEPLOYMENT".equals(e.entityType()));
                    });
        }

        @Test
        void shouldNotThrow_whenNoViolations() {
            var config = configWithDeployment("valid-dep", buildValidDeploymentDto("valid-dep"));
            assertThatCode(() -> importConfigValidator.validate(config)).doesNotThrowAnyException();
        }
    }

    // -- helpers --

    private ExportConfig configWithDeployment(String id, CreateMcpDeploymentRequestDto dto) {
        var dep = buildDomainDeployment(id);
        when(deploymentDtoMapper.toCreateDeploymentRequestDto(dep)).thenReturn(dto);
        var config = ExportConfig.builder().build();
        config.setMcpDeployments(Map.of(id, dep));
        return config;
    }

    private ExportConfig configWithImageDef(String name, McpImageDefinitionRequestDto dto) {
        var imgDef = buildDomainImageDef(name);
        when(imageDefinitionDtoMapper.toImageDefinitionRequestDto(imgDef)).thenReturn(dto);
        var config = ExportConfig.builder().build();
        config.setMcpImageDefinitions(Map.of(name, imgDef));
        return config;
    }

    private McpDeployment buildDomainDeployment(String id) {
        var dep = new McpDeployment();
        dep.setId(id);
        dep.setDisplayName("Valid Display Name");
        dep.setMetadata(new DeploymentMetadata());
        dep.setSource(new InternalImageSource(null, null, "img-name", "1.0.0"));
        return dep;
    }

    private McpImageDefinition buildDomainImageDef(String name) {
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
