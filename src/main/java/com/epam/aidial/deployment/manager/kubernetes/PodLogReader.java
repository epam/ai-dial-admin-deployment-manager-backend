package com.epam.aidial.deployment.manager.kubernetes;

import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.Loggable;
import io.fabric8.kubernetes.client.dsl.TailPrettyLoggable;
import io.fabric8.kubernetes.client.dsl.TimeTailPrettyLoggable;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class PodLogReader {

    private final Integer maxLogCount;
    private final Integer maxLogSize;

    private final Integer tailLogs;
    private final Integer sinceSeconds;
    private final Instant sinceTime;
    private final boolean previous;

    public PodLogReader(PodLogReaderConfiguration configuration) {
        if (configuration.sinceSeconds() != null && configuration.sinceTime() != null) {
            throw new IllegalArgumentException("Only one of 'sinceSeconds' and 'sinceTime' "
                    + "parameters can be specified at the same time.");
        }

        this.maxLogCount = assertGreaterThenZero(configuration.maxLogCount(), "maxLogCount");
        this.maxLogSize = assertGreaterThenZero(configuration.maxLogSize(), "maxLogSize");
        this.tailLogs = assertGreaterThenZero(configuration.tailLogs(), "tailLogs");
        this.sinceSeconds = assertGreaterThenZero(configuration.sinceSeconds(), "sinceSeconds");
        this.sinceTime = configuration.sinceTime();
        this.previous = configuration.previous();
    }

    private static Integer assertGreaterThenZero(Integer value, String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be greater then zero.");
        }
        return value;
    }

    @SneakyThrows
    public void readLogs(ContainerResource containerResource, Consumer<List<String>> logConsumer) {
        try (LogWatch watch = getLoggable(containerResource).watchLog()) {
            readOutputStream(watch.getOutput(), logConsumer);
        } catch (RuntimeException e) {
            log.error("Error occurred during reading logs from k8s.", e);
            logConsumer.accept(List.of("En error occurred during reading logs from k8s. Reason: " + e.getMessage()));
        }
    }

    private Loggable getLoggable(ContainerResource containerResource) {
        var baseResource = previous
                ? containerResource.terminated()
                : containerResource;
        var loggable = applySinceFilter(baseResource);
        return tailLogs != null ? loggable.tailingLines(tailLogs) : loggable;
    }

    private TailPrettyLoggable applySinceFilter(TimeTailPrettyLoggable resource) {
        if (sinceTime != null) {
            return resource.sinceTime(sinceTime.toString());
        } else if (sinceSeconds != null) {
            return resource.sinceSeconds(sinceSeconds);
        }
        return resource;
    }

    private void readOutputStream(InputStream logStream, Consumer<List<String>> logConsumer) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(logStream));
        String line;
        String lastLine = null;
        int logCount = 0;
        var logBatch = new ArrayList<String>();

        while ((line = reader.readLine()) != null) {
            logCount++;
            if (isLogSizeExceeded(logCount)) {
                break;
            }

            logBatch.add(truncate(line));

            if (!reader.ready()) {
                logConsumer.accept(logBatch);
                logBatch = new ArrayList<>();
            }
            lastLine = line;
        }

        if (!logBatch.isEmpty()) {
            logConsumer.accept(logBatch);
        }
        if (isLogSizeExceeded(logCount)) {
            logConsumer.accept(List.of("Logs size exceeded limit"));
        }

        log.info("logs reading task was finished. last line: {}.", lastLine);
    }

    private boolean isLogSizeExceeded(int logCount) {
        return maxLogCount != null && logCount > maxLogCount;
    }

    private String truncate(String line) {
        if (maxLogSize != null) {
            return StringUtils.truncate(line, maxLogSize);
        }
        return line;
    }

}
