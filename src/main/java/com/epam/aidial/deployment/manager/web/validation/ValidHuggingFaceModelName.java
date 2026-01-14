package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a valid HuggingFace model name in the format
 * &lt;user_name&gt;/&lt;model_name&gt; where:
 * <ul>
 *   <li>user_name: Only regular characters and '-' are accepted. '--' are forbidden.
 *       '-' cannot start or end the name. Max length is 42.</li>
 *   <li>model_name: Only regular characters and '-', '_', '.' are accepted. '--' and '..' are forbidden.
 *       '-' and '.' cannot start or end the name. The name cannot end with ".git" or ".ipynb". Max length is 96.</li>
 * </ul>
 */
@Documented
@Constraint(validatedBy = HuggingFaceModelNameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidHuggingFaceModelName {
    String message() default "Invalid HuggingFace model name format. Expected format: <user_name>/<model_name>";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
