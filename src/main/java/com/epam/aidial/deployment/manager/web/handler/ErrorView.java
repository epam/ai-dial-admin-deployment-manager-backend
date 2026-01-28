package com.epam.aidial.deployment.manager.web.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.Objects;

@Data
public class ErrorView {

    private String path;
    private String method;
    private Integer status;
    private String error;
    private String message;

    public ErrorView(HttpServletRequest request, HttpStatus status, String errorMessage) {

        this.path = request.getServletPath();
        this.method = request.getMethod();
        this.status = status.value();
        this.error = status.getReasonPhrase();
        this.message = sanitizeMessage(errorMessage);
    }

    /**
     * Best-effort sanitization to prevent accidental stacktrace leakage in API responses.
     *
     * <p>The API should not rely on this method for correctness; error messages should be sanitized at the
     * exception handling layer. This is a last line of defense.
     */
    private static String sanitizeMessage(String message) {
        String safe = Objects.requireNonNullElse(message, "");
        if (safe.contains("\n\tat ") || safe.contains("\n at ")) {
            // Strip typical Java stack trace lines and only keep the first line.
            int firstLineEnd = safe.indexOf('\n');
            safe = firstLineEnd >= 0 ? safe.substring(0, firstLineEnd) : safe;
        }

        // Defensive cap to avoid returning huge payloads due to accidental propagation of verbose messages.
        int maxChars = 2000;
        if (safe.length() > maxChars) {
            safe = safe.substring(0, maxChars);
        }
        return safe;
    }
}
