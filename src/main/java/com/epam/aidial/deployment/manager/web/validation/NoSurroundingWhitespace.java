package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Pattern(regexp = "^\\S(.*\\S)?$", message = "Path must not contain leading or trailing whitespace")
public @interface NoSurroundingWhitespace {
    String message() default "Path must not contain leading or trailing whitespace";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
