package com.epam.aidial.deployment.manager.kubernetes;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@Getter
public enum JobPhase {

    CREATED("Created", false),
    RUNNING("Running", false),
    SUCCEEDED("Succeeded", true),
    FAILED("Failed", true),
    UNKNOWN("Unknown", false),
    NOT_FOUND("NotFound", false);

    private static final String CONDITION_COMPLETE = "Complete";
    private static final String CONDITION_FAILED = "Failed";
    private static final String CONDITION_STATUS_TRUE = "True";

    private final String value;
    private final boolean isFinal;

    JobPhase(String value, boolean isFinal) {
        this.value = value;
        this.isFinal = isFinal;
    }

    public static JobPhase fromJobStrictly(Job job) {
        return fromJob(job)
                .orElseThrow(() -> new IllegalArgumentException("Cannot get JobState from job: " + job));
    }

    public static Optional<JobPhase> fromJob(Job job) {
        if (job == null) {
            return Optional.empty();
        }
        final var status = job.getStatus();
        if (status == null) {
            return Optional.empty();
        }

        if (hasFailedCondition(status)) {
            return Optional.of(JobPhase.FAILED);
        }

        if (hasCompleteCondition(status)) {
            return Optional.of(JobPhase.SUCCEEDED);
        }

        //sometimes kuber restart pods in job even with restartPolicy=='Never'
        //in that case status will be `active==1 && failed==1`
        //let's think such job in a running state
        if (toInt(status.getActive()) > 0) {
            if (toInt(status.getFailed()) > 0) {
                log.warn("job in broken state: {}", job);
            }
            return Optional.of(JobPhase.RUNNING);
        }

        if (isJobSpecAvailable(job)) {
            return Optional.of(JobPhase.CREATED);
        }

        return Optional.empty();
    }

    private static boolean hasCompleteCondition(JobStatus jobStatus) {
        if (jobStatus.getConditions() == null || jobStatus.getConditions().isEmpty()) {
            return false;
        }
        return jobStatus.getConditions().stream()
                .anyMatch(condition -> CONDITION_COMPLETE.equals(condition.getType()) && CONDITION_STATUS_TRUE.equals(condition.getStatus()));
    }

    private static boolean hasFailedCondition(JobStatus jobStatus) {
        if (jobStatus.getConditions() == null || jobStatus.getConditions().isEmpty()) {
            return false;
        }
        return jobStatus.getConditions().stream()
                .anyMatch(condition -> CONDITION_FAILED.equals(condition.getType()) && CONDITION_STATUS_TRUE.equals(condition.getStatus()));
    }

    private static boolean isJobSpecAvailable(Job job) {
        final var jobSpec = job.getSpec();
        return jobSpec != null;
    }

    private static int toInt(Integer integer) {
        return integer != null ? integer : 0;
    }
}
