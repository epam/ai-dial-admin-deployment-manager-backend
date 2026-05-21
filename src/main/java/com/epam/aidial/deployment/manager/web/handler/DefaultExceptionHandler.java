package com.epam.aidial.deployment.manager.web.handler;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.DatabaseException;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.exception.EntityAlreadyExistsException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.ImageBuildNotInProgressException;
import com.epam.aidial.deployment.manager.exception.ImageBuildStopFailedException;
import com.epam.aidial.deployment.manager.exception.ImageInUseException;
import com.epam.aidial.deployment.manager.exception.ImportValidationException;
import com.epam.aidial.deployment.manager.exception.McpClientException;
import com.epam.aidial.deployment.manager.registry.mcp.client.McpRegistryClientException;
import com.epam.aidial.deployment.manager.service.deployment.MissingTransformerImageException;
import com.epam.aidial.deployment.manager.service.detection.HuggingFaceUpstreamException;
import com.epam.aidial.deployment.manager.service.detection.InferenceTaskDetectionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@LogExecution
public class DefaultExceptionHandler {

    @ExceptionHandler(DeploymentException.class)
    public ErrorView handleDeploymentError(HttpServletRequest req, DeploymentException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(McpClientException.class)
    public ErrorView handleMcpClientError(HttpServletRequest req, McpClientException ex) {
        logUncaught(ex);
        return new ErrorView(req, ex.getHttpStatus(), ex.getMessage());
    }

    @ExceptionHandler(McpRegistryClientException.class)
    public ResponseEntity<ErrorView> handleMcpRegistryClientException(HttpServletRequest req, McpRegistryClientException ex) {
        log.debug("Upstream error: ", ex);
        var status = Optional.ofNullable(HttpStatus.resolve(ex.getStatusCode())).orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorView(req, status, ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({EntityNotFoundException.class, NoResourceFoundException.class})
    public ErrorView handleEntityNotFoundError(HttpServletRequest req, Exception ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class})
    public ErrorView handleMethodNotAllowedError(HttpServletRequest req, Exception ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public ErrorView handleAuthorizationException(HttpServletRequest req, Exception ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorView handleWrongJsonError(HttpServletRequest req, Exception ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingRequestValueException.class)
    public ErrorView handleMissingRequestValueError(HttpServletRequest req, Exception ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ErrorView handleConstraintViolationError(HttpServletRequest req, Exception ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(DatabaseException.class)
    public ErrorView handleDatabaseException(HttpServletRequest req, DatabaseException ex) {
        log.warn("[{}] Request: {} failed due to database error", req.getMethod(), req.getServletPath(), ex);
        return new ErrorView(req, HttpStatus.INTERNAL_SERVER_ERROR, "Database error occurred");
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorView handleGeneralError(HttpServletRequest req, Exception ex) {
        log.warn("[{}] Request: {} raised exception", req.getMethod(), req.getServletPath(), ex);

        return new ErrorView(req, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorView handleValidationExceptions(
            HttpServletRequest req,
            MethodArgumentNotValidException ex) {
        logUncaught(ex);

        var message = new StringBuffer();
        ex.getBindingResult()
                .getAllErrors()
                .forEach(error -> {
                    if (error instanceof FieldError fieldError) {
                        message
                                .append("Field [")
                                .append(fieldError.getField())
                                .append("]: ");
                    }
                    message
                            .append(error.getDefaultMessage())
                            .append("\n");
                });
        return new ErrorView(req, HttpStatus.BAD_REQUEST, message.toString());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ImportValidationException.class)
    public ErrorView handleImportValidationException(HttpServletRequest req, ImportValidationException ex) {
        logUncaught(ex);
        String details = ex.getErrors().stream()
                .map(error -> "[%s '%s'] Field [%s]: %s".formatted(
                    error.entityType(), error.entityIdentifier(), error.fieldPath(), error.message()))
                .collect(Collectors.joining("\n"));
        return new ErrorView(req, HttpStatus.BAD_REQUEST, "Import validation failed:\n" + details);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorView handleIllegalArgumentError(HttpServletRequest req, Exception ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InferenceTaskDetectionException.class)
    public ErrorView handleInferenceTaskDetectionError(HttpServletRequest req, InferenceTaskDetectionException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(HuggingFaceUpstreamException.class)
    public ErrorView handleHuggingFaceUpstreamError(HttpServletRequest req, HuggingFaceUpstreamException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(MissingTransformerImageException.class)
    public ErrorView handleMissingTransformerImage(HttpServletRequest req, MissingTransformerImageException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(EntityAlreadyExistsException.class)
    public ErrorView handleEntityAlreadyExistsException(HttpServletRequest req, EntityAlreadyExistsException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.CONFLICT, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ImageInUseException.class)
    public ErrorView handleImageInUseException(HttpServletRequest req, ImageInUseException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.CONFLICT, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ImageBuildNotInProgressException.class)
    public ErrorView handleImageBuildNotInProgressException(HttpServletRequest req, ImageBuildNotInProgressException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ImageBuildStopFailedException.class)
    public ErrorView handleImageBuildStopFailedException(HttpServletRequest req, ImageBuildStopFailedException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<String> handleAsyncRequestNotUsableException(HttpServletRequest req, AsyncRequestNotUsableException ex) {
        logUncaught(ex);
        log.info("[{}] Request: {} raised AsyncRequestNotUsableException. Streaming has been closed by the client",
                req.getMethod(), req.getServletPath());
        return ResponseEntity.ok("Streaming has been closed by the client.");
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<String> handleAsyncRequestTimeoutException(HttpServletRequest req, AsyncRequestTimeoutException ex) {
        logUncaught(ex);
        log.warn("[{}] Request: {} raised AsyncRequestTimeoutException. Streaming has timed out and should be re-initiated by the client",
                req.getMethod(), req.getServletPath());
        return ResponseEntity.ok("Streaming has timed out and should be re-initiated by the client.");
    }

    protected void logUncaught(final Exception e) {
        if (!log.isDebugEnabled()) {
            return;
        }

        log.debug("Uncaught exception.", e);
    }
}
