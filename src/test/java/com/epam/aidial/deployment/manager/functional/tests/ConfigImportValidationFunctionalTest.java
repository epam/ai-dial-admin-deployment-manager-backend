package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.configuration.ConfigExportProperties;
import com.epam.aidial.deployment.manager.exception.ImportValidationException;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageBuilder;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.config.ConfigExportImportTestHelper;
import com.epam.aidial.deployment.manager.service.config.ConfigTransferService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.web.validation.ImportConfigValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class ConfigImportValidationFunctionalTest {

    @Autowired
    private ConfigExportProperties configExportProperties;
    @Autowired
    private ConfigTransferService configTransferService;
    @Autowired
    private ImportConfigValidator importConfigValidator;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void shouldRejectImport_whenDeploymentHasInvalidName() throws Exception {
        var config = ExportConfig.builder().build();
        var dep = buildValidMcpDeployment("INVALID-UPPERCASE");
        config.setMcpDeployments(Map.of("INVALID-UPPERCASE", dep));

        var zipFile = buildZip(config);
        var parsedConfig = configTransferService.parseAndSanitizeExportConfig(zipFile);

        assertThatThrownBy(() -> importConfigValidator.validate(parsedConfig))
                .isInstanceOf(ImportValidationException.class)
                .satisfies(ex -> {
                    var errors = ((ImportValidationException) ex).getErrors();
                    assertThat(errors).isNotEmpty();
                    assertThat(errors).anyMatch(e -> "MCP_DEPLOYMENT".equals(e.entityType())
                            && "name".equals(e.fieldPath()));
                });
    }

    @Test
    void shouldRejectImport_whenMultipleEntitiesInvalid() throws Exception {
        var config = ExportConfig.builder().build();
        var dep1 = buildValidMcpDeployment("BAD-1");
        var dep2 = buildValidMcpDeployment("BAD-2");
        var deployments = new LinkedHashMap<String, McpDeployment>();
        deployments.put("BAD-1", dep1);
        deployments.put("BAD-2", dep2);
        config.setMcpDeployments(deployments);

        var zipFile = buildZip(config);
        var parsedConfig = configTransferService.parseAndSanitizeExportConfig(zipFile);

        assertThatThrownBy(() -> importConfigValidator.validate(parsedConfig))
                .isInstanceOf(ImportValidationException.class)
                .satisfies(ex -> {
                    var errors = ((ImportValidationException) ex).getErrors();
                    assertThat(errors.size()).isGreaterThanOrEqualTo(2);
                });
    }

    @Test
    void shouldImportSuccessfully_whenAllEntitiesValid() throws Exception {
        var config = ExportConfig.builder().build();
        var imgDef = buildValidMcpImageDefinition("valid-img-test");
        config.setMcpImageDefinitions(Map.of("valid-img-test", imgDef));

        var zipFile = buildZip(config);
        var parsedConfig = configTransferService.parseAndSanitizeExportConfig(zipFile);

        importConfigValidator.validate(parsedConfig);
        configTransferService.importConfig(parsedConfig, ConflictResolutionPolicy.OVERWRITE);

        var created = imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(
                com.epam.aidial.deployment.manager.model.ImageType.MCP, "valid-img-test", "1.0.0");
        assertThat(created).isPresent();
    }

    @Test
    void shouldStripInjectedStatusField_whenMaliciousZipImported() throws Exception {
        var imgDef = buildValidMcpImageDefinition("sanitize-img-test");

        // Insert a deployment with injected status
        var maliciousConfig = ExportConfig.builder().build();
        var dep = buildValidMcpDeployment("sanitize-dep-test");
        dep.setStatus(DeploymentStatus.RUNNING);
        dep.setSource(new InternalImageSource(null, null, "sanitize-img-test", "1.0.0"));
        maliciousConfig.setMcpImageDefinitions(Map.of("sanitize-img-test", imgDef));
        maliciousConfig.setMcpDeployments(Map.of("sanitize-dep-test", dep));

        var zipFile = buildZip(maliciousConfig);
        var parsedConfig = configTransferService.parseAndSanitizeExportConfig(zipFile);

        // After sanitization, status should be stripped (null)
        var sanitizedDep = parsedConfig.getMcpDeployments().get("sanitize-dep-test");
        assertThat(sanitizedDep).isNotNull();
        assertThat(sanitizedDep.getStatus()).isNull();
    }

    @Test
    void shouldStripInjectedAuthorField_whenMaliciousZipImported() throws Exception {
        var config = ExportConfig.builder().build();
        var dep = buildValidMcpDeployment("author-test-dep");
        dep.setAuthor("malicious-author@evil.com");
        dep.setSource(new InternalImageSource(null, null, "some-img", "1.0.0"));
        config.setMcpDeployments(Map.of("author-test-dep", dep));

        var zipFile = buildZip(config);
        var parsedConfig = configTransferService.parseAndSanitizeExportConfig(zipFile);

        var sanitizedDep = parsedConfig.getMcpDeployments().get("author-test-dep");
        assertThat(sanitizedDep).isNotNull();
        assertThat(sanitizedDep.getAuthor()).isNull();
    }

    @Test
    void shouldIncludeValidationErrorsInPreview_whenEntitiesInvalid() throws Exception {
        var config = ExportConfig.builder().build();
        var dep = buildValidMcpDeployment("INVALID-PREVIEW");
        config.setMcpDeployments(Map.of("INVALID-PREVIEW", dep));

        var zipFile = buildZip(config);
        var parsedConfig = configTransferService.parseAndSanitizeExportConfig(zipFile);

        var errors = importConfigValidator.collectErrors(parsedConfig);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> "MCP_DEPLOYMENT".equals(e.entityType()));
    }

    private MultipartFile buildZip(ExportConfig config) throws Exception {
        byte[] zipBytes = ConfigExportImportTestHelper.buildZipFromExportConfig(
                configExportProperties.getFileName(), config, jsonMapper);
        return ConfigExportImportTestHelper.createZipMultipartFile("test-import.zip", zipBytes);
    }

    private McpDeployment buildValidMcpDeployment(String id) {
        var dep = new McpDeployment();
        dep.setId(id);
        dep.setDisplayName("Valid Display Name");
        dep.setMetadata(new DeploymentMetadata());
        dep.setSource(new InternalImageSource(null, ImageType.MCP, "img-name", "1.0.0"));
        dep.setResources(new Resources(Map.of(), Map.of()));
        dep.setAllowedDomains(List.of());
        dep.setTopics(List.of());
        return dep;
    }

    private McpImageDefinition buildValidMcpImageDefinition(String name) {
        var imgDef = new McpImageDefinition();
        imgDef.setName(name);
        imgDef.setVersion("1.0.0");
        imgDef.setSource(new DockerImageSource("docker.io/test:latest", null, null));
        imgDef.setImageBuilder(ImageBuilder.BUILDKIT);
        imgDef.setAllowedDomains(List.of());
        imgDef.setTopics(List.of());
        return imgDef;
    }
}
