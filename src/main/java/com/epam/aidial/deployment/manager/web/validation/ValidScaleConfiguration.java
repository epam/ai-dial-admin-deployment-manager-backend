package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that scale-related fields on {@code CreateDeploymentRequestDto}
 * satisfy the following invariant where values are provided:
 * 0 <= minScale <= initialScale <= maxScale <= 10.
 */
@Documented
@Constraint(validatedBy = ScaleConfigurationValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidScaleConfiguration {

    String message() default "Scale values must satisfy 0 <= minScale <= initialScale <= maxScale <= 10";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

