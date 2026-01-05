package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that ResourcesDto has values in 'limits' that are always greater than or equal to values in 'requests'.
 * Also validates that values in limits and requests are numeric.
 */
@Documented
@Constraint(validatedBy = ResourcesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidResources {
    String message() default "Resource limits and requests should be numeric. Limits must be greater than or equal to requests.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}