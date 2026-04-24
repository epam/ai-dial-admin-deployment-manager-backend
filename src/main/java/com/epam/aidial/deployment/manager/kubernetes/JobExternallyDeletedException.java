package com.epam.aidial.deployment.manager.kubernetes;

/**
 * Thrown when a Job that the runner was waiting on disappears from the cluster API before reaching a terminal state.
 * Signals external termination (e.g. an administrator stop action) so that callers can distinguish it from a
 * genuine build failure and avoid recording BUILD_FAILED on a run that was cancelled out-of-band.
 */
public class JobExternallyDeletedException extends RuntimeException {

    public JobExternallyDeletedException(String jobId) {
        super("Job '%s' was externally deleted while the runner was waiting for it".formatted(jobId));
    }
}
