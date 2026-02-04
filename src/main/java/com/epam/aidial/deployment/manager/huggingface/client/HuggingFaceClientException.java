package com.epam.aidial.deployment.manager.huggingface.client;

import lombok.Getter;

/**
 * Exception thrown when a Hugging Face API request fails with a specific HTTP status code.
 */
@Getter
public class HuggingFaceClientException extends RuntimeException {

    private final int statusCode;

    public HuggingFaceClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

}
