package com.epam.aidial.deployment.manager.web.validation;

import com.epam.aidial.deployment.manager.utils.KubernetesQuantityParser;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;

@Slf4j
public class StorageSizeValidator implements ConstraintValidator<ValidStorageSize, String> {

    @Value("${app.validation.resources.max-storage-size}")
    private String maxStorageSize;

    private BigDecimal maxStorageBytes;

    @Override
    public void initialize(ValidStorageSize constraintAnnotation) {
        if (maxStorageSize == null) {
            maxStorageBytes = null;
            return;
        }
        maxStorageBytes = KubernetesQuantityParser.parseToBytes(maxStorageSize);
        if (maxStorageBytes == null) {
            throw new IllegalStateException(
                    "Invalid app.validation.resources.max-storage-size: '%s'. Must be a valid Kubernetes quantity."
                            .formatted(maxStorageSize));
        }
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        BigDecimal bytes = KubernetesQuantityParser.parseToBytes(value);
        if (bytes == null) {
            log.debug("Invalid storage size format: '{}'", value);
            return false;
        }

        if (bytes.compareTo(BigDecimal.ONE) < 0) {
            log.debug("Storage size must be positive: '{}'", value);
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Storage size must be a positive value")
                    .addConstraintViolation();
            return false;
        }

        if (maxStorageBytes != null && bytes.compareTo(maxStorageBytes) > 0) {
            log.debug("Storage size '{}' exceeds max allowed '{}'", value, maxStorageSize);
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            "Storage size must not exceed %s".formatted(maxStorageSize))
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
