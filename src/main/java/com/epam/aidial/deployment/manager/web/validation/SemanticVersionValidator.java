package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator for semantic versions using the format a.b.c where a, b, and c are non-negative integers.
 */
public class SemanticVersionValidator implements ConstraintValidator<ValidSemanticVersion, String> {

    private static final Pattern SEMANTIC_VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }

        return SEMANTIC_VERSION_PATTERN.matcher(value).matches();
    }
}

