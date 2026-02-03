package com.epam.aidial.deployment.manager.web.validation;

import com.epam.aidial.deployment.manager.web.dto.deployment.CreateDeploymentRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScaleConfigurationValidator implements ConstraintValidator<ValidScaleConfiguration, CreateDeploymentRequestDto> {

    private static final int MIN_ALLOWED_SCALE = 0;
    private static final int MAX_ALLOWED_SCALE = 10;

    @Override
    public boolean isValid(CreateDeploymentRequestDto value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        Integer minScale = value.getMinScale();
        Integer initialScale = value.getInitialScale();
        Integer maxScale = value.getMaxScale();

        log.debug("Validating scale configuration: minScale={}, initialScale={}, maxScale={}", minScale, initialScale, maxScale);

        // If all are null, treat as not specified and valid.
        if (minScale == null && initialScale == null && maxScale == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        // Range checks
        if (!isWithinAllowedRange(minScale)) {
            addViolation(context, "minScale must be between %d and %d".formatted(MIN_ALLOWED_SCALE, MAX_ALLOWED_SCALE));
            valid = false;
        }

        if (!isWithinAllowedRange(initialScale)) {
            addViolation(context, "initialScale must be between %d and %d".formatted(MIN_ALLOWED_SCALE, MAX_ALLOWED_SCALE));
            valid = false;
        }

        if (!isWithinAllowedRange(maxScale)) {
            addViolation(context, "maxScale must be between %d and %d".formatted(MIN_ALLOWED_SCALE, MAX_ALLOWED_SCALE));
            valid = false;
        }

        // Ordering checks (only where both sides are provided)
        if (minScale != null && initialScale != null && minScale > initialScale) {
            addViolation(context, "minScale must be less than or equal to initialScale");
            valid = false;
        }

        if (initialScale != null && maxScale != null && initialScale > maxScale) {
            addViolation(context, "initialScale must be less than or equal to maxScale");
            valid = false;
        }

        if (minScale != null && maxScale != null && minScale > maxScale) {
            addViolation(context, "minScale must be less than or equal to maxScale");
            valid = false;
        }

        return valid;
    }

    private boolean isWithinAllowedRange(Integer value) {
        if (value == null) {
            return true;
        }
        return value >= MIN_ALLOWED_SCALE && value <= MAX_ALLOWED_SCALE;
    }

    private void addViolation(ConstraintValidatorContext context, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
}

