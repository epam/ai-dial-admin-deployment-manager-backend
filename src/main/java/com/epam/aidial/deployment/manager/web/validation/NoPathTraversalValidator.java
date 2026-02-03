package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator that prohibits path traversal patterns in strings.
 * Checks for patterns like "../", "./", "/..", etc.
 */
public class NoPathTraversalValidator implements ConstraintValidator<NoPathTraversal, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        // 1. Block Tilde "~"
        if (value.contains("~")) {
            return addViolation(context, "Path must not contain the tilde character ('~')");
        }

        // 2. Block Start/End Slash
        if (value.startsWith("/") || value.endsWith("/")) {
            return addViolation(context, "Path must not start or end with '/'");
        }

        // 3. Block Current Directory Traversal "."
        if (value.equals(".") || value.startsWith("./") || value.contains("/./") || value.endsWith("/.")) {
            return addViolation(context, "Path must not contain current directory references ('.', './')");
        }

        // 4. Block Parent Directory Traversal ".."
        if (value.equals("..") || value.startsWith("../") || value.contains("/../") || value.endsWith("/..")) {
            return addViolation(context, "Path must not contain parent directory references ('..', '../')");
        }

        return true;
    }

    /**
     * Helper to disable default message and add a custom one.
     * Returns false for convenience so it can be used in return statements.
     */
    private boolean addViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
        return false;
    }

}
