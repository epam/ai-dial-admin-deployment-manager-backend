package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a valid Docker image name.
 * Uses Jib's ImageReference.parse to validate the image name format.
 */
@Documented
@Constraint(validatedBy = DockerImageNameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDockerImageName {
    String message() default "Invalid Docker image URI format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}