package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string does not contain path traversal patterns.
 * Prohibits patterns like "../", "./", "/..", etc.
 */
@Documented
@Constraint(validatedBy = NoPathTraversalValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoPathTraversal {
    String message() default "Path must not contain path traversal patterns";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
