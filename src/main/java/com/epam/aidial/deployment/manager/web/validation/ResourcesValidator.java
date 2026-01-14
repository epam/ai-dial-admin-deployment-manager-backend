package com.epam.aidial.deployment.manager.web.validation;

import com.epam.aidial.deployment.manager.web.dto.ResourcesDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ResourcesValidator implements ConstraintValidator<ValidResources, ResourcesDto> {

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

        // Find intersection of keys
        Set<String> commonKeys = new HashSet<>(limits.keySet());
        commonKeys.retainAll(requests.keySet());
        log.debug("Common resource keys to validate: {}", commonKeys);

        for (String key : commonKeys) {
            String requestValue = requests.get(key);
            String limitValue = limits.get(key);

            log.debug("Validating resource '{}': request='{}', limit='{}'", key, requestValue, limitValue);

            if (isNumeric(requestValue) && isNumeric(limitValue)) {
                double requestNumeric = parseNumeric(requestValue);
                double limitNumeric = parseNumeric(limitValue);

                log.debug("Parsed numeric values for '{}': request={}, limit={}", key, requestNumeric, limitNumeric);

                if (limitNumeric < requestNumeric) {
                    log.warn("Validation failed for '{}': limit ({}) < request ({})", key, limitNumeric, requestNumeric);
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate(
                            "Limit value for '%s' must be greater than or equal to request value".formatted(key))
                            .addConstraintViolation();
                    return false;
                }
            } else {
                log.debug("Non-numeric values for '{}': request='{}', limit='{}'", key, requestValue, limitValue);
                return false;
            }
        }

        log.debug("ResourcesDto validation passed.");
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