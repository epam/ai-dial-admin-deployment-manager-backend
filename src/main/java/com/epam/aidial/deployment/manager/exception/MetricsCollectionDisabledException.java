package com.epam.aidial.deployment.manager.exception;

/**
 * Signals that the metrics-collection feature is disabled by configuration
 * ({@code app.metrics.scrape.enabled=false}). This is an intended config state, not a fault — it
 * maps to HTTP 400 per the documented contract and is not logged as an uncaught error.
 */
public class MetricsCollectionDisabledException extends RuntimeException {

    public MetricsCollectionDisabledException(String message) {
        super(message);
    }
}
