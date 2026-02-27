package com.epam.aidial.deployment.manager.web.validation;

import com.epam.aidial.deployment.manager.web.dto.ScalingDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScalingValidator implements ConstraintValidator<ValidScaling, ScalingDto> {

    @Override
    public boolean isValid(ScalingDto value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        int minReplicas = value.getMinReplicas();
        int maxReplicas = value.getMaxReplicas();

        log.debug("Validating scaling configuration: minReplicas={}, maxReplicas={}", minReplicas, maxReplicas);

        if (minReplicas > maxReplicas) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("minReplicas must be less than or equal to maxReplicas")
                    .addConstraintViolation();
            return false;
        }

        if (minReplicas == maxReplicas && value.getStrategy() != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("strategy must be null when minReplicas equals maxReplicas")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
