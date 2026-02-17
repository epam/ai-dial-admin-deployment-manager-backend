package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that scale-related fields on {@code ScalingDto}
 * satisfy the following invariant:
 * 0 <= minReplicas <= maxReplicas.
 */
@Documented
@Constraint(validatedBy = ScalingValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidScaling {

    String message() default "Scale values must satisfy 0 <= minReplicas <= maxReplicas";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
