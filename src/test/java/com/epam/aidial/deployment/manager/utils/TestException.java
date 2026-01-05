package com.epam.aidial.deployment.manager.utils;

public class TestException extends RuntimeException {
    public TestException() {
    }

    public TestException(String message) {
        super(message);
    }

    public TestException(String message, Throwable cause) {
        super(message, cause);
    }
}
