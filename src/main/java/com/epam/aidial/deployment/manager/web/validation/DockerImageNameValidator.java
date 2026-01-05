package com.epam.aidial.deployment.manager.web.validation;

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for Docker image URIs using Jib's ImageReference.parse.
 */
public class DockerImageNameValidator implements ConstraintValidator<ValidDockerImageName, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }

        try {
            ImageReference.parse(value);
            return true;
        } catch (InvalidImageReferenceException e) {
            return false;
        }
    }
}