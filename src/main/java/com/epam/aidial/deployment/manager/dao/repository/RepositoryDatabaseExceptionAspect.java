package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.exception.DatabaseException;
import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Wraps low-level database exceptions thrown from {@code com.epam.aidial.deployment.manager.dao.repository}
 * into a {@link DatabaseException} to prevent leaking SQL/JPA provider details to the API layer.
 */
@Aspect
@Component
@Slf4j
public class RepositoryDatabaseExceptionAspect {

    @Around("execution(public * com.epam.aidial.deployment.manager.dao.repository..*(..))")
    public Object wrapRepositoryDatabaseExceptions(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (DataAccessException | PersistenceException e) {
            log.warn("Database exception in {}.{}()",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    e);
            throw new DatabaseException("Database operation failed", e);
        }
    }
}

