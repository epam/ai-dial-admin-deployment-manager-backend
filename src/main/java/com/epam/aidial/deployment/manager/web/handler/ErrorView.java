package com.epam.aidial.deployment.manager.web.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.http.HttpStatus;

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
        this.message = errorMessage;
    }
}
