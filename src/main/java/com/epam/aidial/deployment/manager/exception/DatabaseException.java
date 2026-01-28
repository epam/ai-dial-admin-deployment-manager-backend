package com.epam.aidial.deployment.manager.exception;

/**
 * Exception indicating a database-related failure.
 *
 * <p>This exception is intended to be thrown at the DAO/repository layer boundary to avoid leaking
 * vendor-specific SQL/JPA exception details outside the service.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}

