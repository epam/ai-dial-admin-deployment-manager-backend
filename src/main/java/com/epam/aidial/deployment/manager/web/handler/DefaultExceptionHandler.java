package com.epam.aidial.deployment.manager.web.handler;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.ImageInUseException;
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

@RestControllerAdvice
@Slf4j
@LogExecution
public class DefaultExceptionHandler {

    @ExceptionHandler(DeploymentException.class)
    public ErrorView handleDeploymentError(HttpServletRequest req, DeploymentException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
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

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AccessDeniedException.class)
    public ErrorView handleAuthorizationException(HttpServletRequest req, Exception ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.UNAUTHORIZED, ex.getMessage());
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
    @ExceptionHandler(Exception.class)
    public ErrorView handleGeneralError(HttpServletRequest req, Exception ex) {
        log.warn("[{}] Request: {} raised exception", req.getMethod(), req.getServletPath(), ex);

        return new ErrorView(req, HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
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
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorView handleIllegalArgumentError(HttpServletRequest req, Exception ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ImageInUseException.class)
    public ErrorView handleImageInUseException(HttpServletRequest req, ImageInUseException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.CONFLICT, ex.getMessage());
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
