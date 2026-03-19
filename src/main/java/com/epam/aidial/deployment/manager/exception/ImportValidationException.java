package com.epam.aidial.deployment.manager.exception;

import com.epam.aidial.deployment.manager.model.config.ImportValidationError;
import lombok.Getter;

import java.util.List;

@Getter
public class ImportValidationException extends RuntimeException {

    private final List<ImportValidationError> errors;

    public ImportValidationException(List<ImportValidationError> errors) {
        super("Import validation failed: %d violation(s)".formatted(errors.size()));
        this.errors = errors;
    }
}
