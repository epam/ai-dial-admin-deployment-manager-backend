package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a valid semantic version in the format a.b.c
 * where a, b, and c are non-negative integers.
 */
@Documented
@Constraint(validatedBy = SemanticVersionValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSemanticVersion {
    String message() default "Invalid semantic version format. Expected format: a.b.c (e.g., 1.0.0)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

