package com.epam.aidial.deployment.manager.exception;

public class EntityNotFoundException extends ValidationException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
