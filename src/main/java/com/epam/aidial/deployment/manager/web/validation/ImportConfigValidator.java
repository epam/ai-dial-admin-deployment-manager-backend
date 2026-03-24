package com.epam.aidial.deployment.manager.web.validation;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.ImportValidationException;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ExportConfigComponentType;
import com.epam.aidial.deployment.manager.model.config.ImportValidationError;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.mapper.DeploymentDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionDtoMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class ImportConfigValidator {

    private final Validator validator;
    private final DeploymentDtoMapper deploymentDtoMapper;
    private final ImageDefinitionDtoMapper imageDefinitionDtoMapper;

    public void validate(ExportConfig config) {
        var errors = collectErrors(config);
        if (!errors.isEmpty()) {
            throw new ImportValidationException(errors);
        }
    }

    public List<ImportValidationError> collectErrors(ExportConfig config) {
        var errors = new ArrayList<ImportValidationError>();
        validateDeployments(config.getMcpDeployments(), ExportConfigComponentType.MCP_DEPLOYMENT, errors);
        validateDeployments(config.getAdapterDeployments(), ExportConfigComponentType.ADAPTER_DEPLOYMENT, errors);
        validateDeployments(config.getApplicationDeployments(), ExportConfigComponentType.APPLICATION_DEPLOYMENT, errors);
        validateDeployments(config.getInterceptorDeployments(), ExportConfigComponentType.INTERCEPTOR_DEPLOYMENT, errors);
        validateDeployments(config.getNimDeployments(), ExportConfigComponentType.NIM_DEPLOYMENT, errors);
        validateDeployments(config.getInferenceDeployments(), ExportConfigComponentType.INFERENCE_DEPLOYMENT, errors);
        validateImageDefinitions(config.getMcpImageDefinitions(), ExportConfigComponentType.MCP_IMAGE_DEFINITION, errors);
        validateImageDefinitions(config.getAdapterImageDefinitions(), ExportConfigComponentType.ADAPTER_IMAGE_DEFINITION, errors);
        validateImageDefinitions(config.getInterceptorImageDefinitions(), ExportConfigComponentType.INTERCEPTOR_IMAGE_DEFINITION, errors);
        validateImageDefinitions(config.getApplicationImageDefinitions(), ExportConfigComponentType.APPLICATION_IMAGE_DEFINITION, errors);
        validateGlobalDomainWhitelist(config.getGlobalImageBuildDomainWhitelist(), errors);
        return errors;
    }

    private void validateDeployments(Map<String, ? extends Deployment> deployments, ExportConfigComponentType entityType,
                                     List<ImportValidationError> errors) {
        if (deployments == null) {
            return;
        }
        for (Map.Entry<String, ? extends Deployment> entry : deployments.entrySet()) {
            var deployment = entry.getValue();
            var identifier = deployment.getId() != null ? deployment.getId() : entry.getKey();
            try {
                var dto = deploymentDtoMapper.toCreateDeploymentRequestDto(deployment);
                Set<ConstraintViolation<CreateDeploymentRequestDto>> violations = validator.validate(dto);
                for (var violation : violations) {
                    errors.add(new ImportValidationError(entityType.name(), identifier,
                            violation.getPropertyPath().toString(), violation.getMessage()));
                }
            } catch (Exception e) {
                errors.add(new ImportValidationError(entityType.name(), identifier, "", "Mapping failed: " + e.getMessage()));
            }
        }
    }

    private void validateImageDefinitions(Map<String, ? extends ImageDefinition> definitions, ExportConfigComponentType entityType,
                                          List<ImportValidationError> errors) {
        if (definitions == null) {
            return;
        }
        for (Map.Entry<String, ? extends ImageDefinition> entry : definitions.entrySet()) {
            var definition = entry.getValue();
            var identifier = entry.getKey();
            try {
                var dto = imageDefinitionDtoMapper.toImageDefinitionRequestDto(definition);
                Set<ConstraintViolation<ImageDefinitionRequestDto>> violations = validator.validate(dto);
                for (var violation : violations) {
                    errors.add(new ImportValidationError(entityType.name(), identifier,
                            violation.getPropertyPath().toString(), violation.getMessage()));
                }
            } catch (Exception e) {
                errors.add(new ImportValidationError(entityType.name(), identifier, "", "Mapping failed: " + e.getMessage()));
            }
        }
    }

    private void validateGlobalDomainWhitelist(List<String> whitelist, List<ImportValidationError> errors) {
        if (CollectionUtils.isEmpty(whitelist)) {
            return;
        }
        for (String domain : whitelist) {
            if (!DomainListValidator.isValidDomain(domain)) {
                errors.add(new ImportValidationError("GLOBAL_DOMAIN_WHITELIST", "",
                        "globalImageBuildDomainWhitelist",
                        domain == null ? "domain must not be null"
                                : "domain '%s' is not a valid domain name".formatted(domain)));
            }
        }
    }
}
