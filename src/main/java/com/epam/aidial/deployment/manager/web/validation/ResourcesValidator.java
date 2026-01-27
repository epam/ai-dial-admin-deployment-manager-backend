package com.epam.aidial.deployment.manager.web.validation;

import com.epam.aidial.deployment.manager.web.dto.ResourcesDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ResourcesValidator implements ConstraintValidator<ValidResources, ResourcesDto> {

    private static final String CPU = "cpu";
    private static final String MEMORY = "memory";

    @Value("${app.validation.resources.max-cpu-in-cores}")
    private double maxCpuResourceLimitInCores;

    @Value("${app.validation.resources.max-memory-in-mb}")
    private double maxMemoryResourceLimitInMb;

    @Override
    public boolean isValid(ResourcesDto resources, ConstraintValidatorContext context) {
        log.debug("Starting validation for ResourcesDto: {}", resources);

        if (resources == null) {
            log.debug("ResourcesDto is null, skipping validation.");
            return true;
        }

        Map<String, String> limits = resources.limits();
        Map<String, String> requests = resources.requests();

        if (limits == null || requests == null) {
            log.debug("Limits or requests are null (limits: {}, requests: {}), skipping validation.", limits, requests);
            return true;
        }

        return validateCommonResourceKeys(limits, requests, context);
    }

    private boolean validateCommonResourceKeys(Map<String, String> limits,
                                               Map<String, String> requests,
                                               ConstraintValidatorContext context) {
        Set<String> commonKeys = new HashSet<>(limits.keySet());
        commonKeys.retainAll(requests.keySet());
        log.debug("Common resource keys to validate: {}", commonKeys);

        for (String key : commonKeys) {
            if (!validateSingleResource(key, requests.get(key), limits.get(key), context)) {
                return false;
            }
        }

        log.debug("ResourcesDto validation passed.");
        return true;
    }

    private boolean validateSingleResource(String key,
                                           String requestValue,
                                           String limitValue,
                                           ConstraintValidatorContext context) {
        log.debug("Validating resource '{}': request='{}', limit='{}'", key, requestValue, limitValue);

        if (!isNumeric(requestValue) || !isNumeric(limitValue)) {
            log.debug("Non-numeric values for '{}': request='{}', limit='{}'", key, requestValue, limitValue);
            return false;
        }

        double requestNumeric = parseNumeric(requestValue);
        double limitNumeric = parseNumeric(limitValue);

        log.debug("Parsed numeric values for '{}': request={}, limit={}", key, requestNumeric, limitNumeric);

        if (!validatePositiveValues(key, requestNumeric, limitNumeric, context)) {
            return false;
        }

        if (!validateUpperBounds(key, requestNumeric, limitNumeric, context)) {
            return false;
        }

        return validateLimitNotLessThanRequest(key, requestNumeric, limitNumeric, context);
    }

    private boolean validatePositiveValues(String key,
                                           double requestNumeric,
                                           double limitNumeric,
                                           ConstraintValidatorContext context) {
        // Lower bound check: both values must be greater than 0
        if (requestNumeric <= 0 || limitNumeric <= 0) {
            log.warn("Validation failed for '{}': values must be greater than 0 (request={}, limit={})",
                    key, requestNumeric, limitNumeric);
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            "Request and limit values for '%s' must be greater than 0".formatted(key))
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    private boolean validateUpperBounds(String key,
                                        double requestNumeric,
                                        double limitNumeric,
                                        ConstraintValidatorContext context) {
        // Upper bound checks for CPU and memory
        if (key.equalsIgnoreCase(CPU)) {
            if (requestNumeric > maxCpuResourceLimitInCores || limitNumeric > maxCpuResourceLimitInCores) {
                log.warn(
                        "Validation failed for '{}': values exceed max allowed cores (request={}, limit={}, max={})",
                        key, requestNumeric, limitNumeric, maxCpuResourceLimitInCores);
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "Request and limit for '%s' must not exceed %s cores"
                                        .formatted(key, maxCpuResourceLimitInCores))
                        .addConstraintViolation();
                return false;
            }
        } else if (key.equalsIgnoreCase(MEMORY)) {
            double maxMemoryResourceLimitInBytes = maxMemoryResourceLimitInMb * 1_000_000;
            if (requestNumeric > maxMemoryResourceLimitInBytes || limitNumeric > maxMemoryResourceLimitInBytes) {
                log.warn(
                        "Validation failed for '{}': values exceed max allowed bytes (request={}, limit={}, max={})",
                        key, requestNumeric, limitNumeric, maxMemoryResourceLimitInBytes);
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "Request and limit for '%s' must not exceed %s bytes"
                                        .formatted(key, (long) maxMemoryResourceLimitInBytes))
                        .addConstraintViolation();
                return false;
            }
        }
        return true;
    }

    private boolean validateLimitNotLessThanRequest(String key,
                                                    double requestNumeric,
                                                    double limitNumeric,
                                                    ConstraintValidatorContext context) {
        if (limitNumeric < requestNumeric) {
            log.warn("Validation failed for '{}': limit ({}) < request ({})", key, limitNumeric, requestNumeric);
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            "Limit value for '%s' must be greater than or equal to request value".formatted(key))
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            log.debug("Value is null or empty: '{}'", value);
            return false;
        }

        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            log.debug("Value is not numeric: '{}'", value);
            return false;
        }
    }

    private double parseNumeric(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse numeric value: '{}'", value);
            throw e;
        }
    }
}