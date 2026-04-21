package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a storage size string is a valid Kubernetes resource quantity
 * (e.g., "20Gi", "500Mi", "1Ti", or plain bytes like "21474836480")
 * and does not exceed the configured maximum.
 */
@Documented
@Constraint(validatedBy = StorageSizeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidStorageSize {
    String message() default "Invalid storage size. Must be a valid Kubernetes quantity (e.g., '20Gi', '500Mi') "
            + "or plain bytes (e.g., '21474836480')";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
