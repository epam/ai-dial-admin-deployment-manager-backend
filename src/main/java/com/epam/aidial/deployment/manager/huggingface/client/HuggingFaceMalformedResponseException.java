package com.epam.aidial.deployment.manager.huggingface.client;

import org.springframework.http.HttpStatus;

/**
 * Raised when the Hugging Face API returns a 2xx response whose body cannot be parsed
 * (e.g. malformed JSON for a model's {@code config.json}). Distinct from
 * {@link HuggingFaceClientException} with a transport/HTTP status because retrying will
 * not help — the upstream data itself is unusable.
 */
public class HuggingFaceMalformedResponseException extends HuggingFaceClientException {

    public HuggingFaceMalformedResponseException(String message, Throwable cause) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY.value(), cause);
    }
}
