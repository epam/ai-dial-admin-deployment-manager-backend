package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for HuggingFace model names in the format &lt;user_name&gt;/&lt;model_name&gt;.
 */
public class HuggingFaceModelNameValidator implements ConstraintValidator<ValidHuggingFaceModelName, String> {

    private static final int MAX_USER_NAME_LENGTH = 42;
    private static final int MAX_MODEL_NAME_LENGTH = 96;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }

        // Check format: must contain exactly one '/'
        int slashIndex = value.indexOf('/');
        if (slashIndex == -1 || slashIndex != value.lastIndexOf('/')) {
            return false;
        }

        String userName = value.substring(0, slashIndex);
        String modelName = value.substring(slashIndex + 1);

        return isValidUserName(userName) && isValidModelName(modelName);
    }

    private boolean isValidUserName(String userName) {
        if (userName == null || userName.isEmpty()) {
            return false;
        }

        // Check max length
        if (userName.length() > MAX_USER_NAME_LENGTH) {
            return false;
        }

        // Check if starts or ends with '-'
        if (userName.startsWith("-") || userName.endsWith("-")) {
            return false;
        }

        // Check for '--' (double dash)
        if (userName.contains("--")) {
            return false;
        }

        // Check that only regular characters and '-' are used
        // Regular characters are alphanumeric: [a-zA-Z0-9]
        for (char c : userName.toCharArray()) {
            if (!isAlphanumeric(c) && c != '-') {
                return false;
            }
        }

        return true;
    }

    private boolean isValidModelName(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return false;
        }

        // Check max length
        if (modelName.length() > MAX_MODEL_NAME_LENGTH) {
            return false;
        }

        // Check if starts or ends with '-' or '.'
        if (modelName.startsWith("-") || modelName.endsWith("-")
                || modelName.startsWith(".") || modelName.endsWith(".")) {
            return false;
        }

        // Check for '--' (double dash) or '..' (double dot)
        if (modelName.contains("--") || modelName.contains("..")) {
            return false;
        }

        // Check that name doesn't end with ".git" or ".ipynb"
        if (modelName.endsWith(".git") || modelName.endsWith(".ipynb")) {
            return false;
        }

        // Check that only regular characters, '-', '_', and '.' are used
        // Regular characters are alphanumeric: [a-zA-Z0-9]
        for (char c : modelName.toCharArray()) {
            if (!isAlphanumeric(c) && c != '-' && c != '_' && c != '.') {
                return false;
            }
        }

        return true;
    }

    private boolean isAlphanumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
