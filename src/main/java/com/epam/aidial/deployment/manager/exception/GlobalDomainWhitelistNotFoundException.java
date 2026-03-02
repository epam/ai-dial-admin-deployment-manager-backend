package com.epam.aidial.deployment.manager.exception;

public class GlobalDomainWhitelistNotFoundException extends RuntimeException {

    private static final String MESSAGE = "Global domain whitelist not found";

    public GlobalDomainWhitelistNotFoundException() {
        super(MESSAGE);
    }

    public GlobalDomainWhitelistNotFoundException(String message) {
        super(message);
    }
}
