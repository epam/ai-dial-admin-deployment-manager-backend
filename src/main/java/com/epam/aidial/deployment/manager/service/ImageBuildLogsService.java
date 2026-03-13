package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.AccessedDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class ImageBuildLogsService {

    private final ImageDefinitionService imageDefinitionService;
    private final SseEmitterFactory sseEmitterFactory;
    @Qualifier("sse-streamer")
    private final ExecutorService executorService;
    
    @Value("${app.sse.poll-interval-ms:1000}")
    private final long pollIntervalMs;
    @Value("${app.sse.min-streaming-interval-ms:2000}")
    private final long minStreamingIntervalMs;

    public SseEmitter streamLogs(UUID imageDefinitionId) {
        return sseEmitterFactory.createEmitter(
                String.valueOf(imageDefinitionId),
                "ImageBuild-log",
                emitter -> startLogStreaming(imageDefinitionId, emitter)
        );
    }

    public SseEmitter streamStatus(UUID imageDefinitionId) {
        return sseEmitterFactory.createEmitter(
                String.valueOf(imageDefinitionId),
                "ImageBuild-status",
                emitter -> startStatusStreaming(imageDefinitionId, emitter)
        );
    }

    public SseEmitter streamAccessedDomains(UUID imageDefinitionId) {
        return sseEmitterFactory.createEmitter(
                String.valueOf(imageDefinitionId),
                "ImageBuild-accessed-domains",
                emitter -> startAccessedDomainsStreaming(imageDefinitionId, emitter)
        );
    }

    private SafeAutoCloseable startAccessedDomainsStreaming(UUID imageDefinitionId, SseEmitter emitter) {
        var domainIndex = new AtomicInteger(0);
        var lastStatus = new AtomicInteger(-1);
        var startTime = Instant.now();

        Future<?> future = executorService.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    var imageDefinition = imageDefinitionService.getImageDefinition(imageDefinitionId)
                            .orElseThrow(() -> new EntityNotFoundException("ImageDefinition not found: %s".formatted(imageDefinitionId)));

                    List<AccessedDomain> domains = imageDefinition.getAccessedDomains();
                    if (domains != null) {
                        while (domainIndex.get() < domains.size()) {
                            AccessedDomain d = domains.get(domainIndex.getAndIncrement());
                            if (d != null && d.getDomain() != null) {
                                try {
                                    Map<String, String> payload = Map.of(
                                            "domain", d.getDomain(),
                                            "verdict", d.getVerdict() != null ? d.getVerdict().name() : "BLOCKED"
                                    );
                                    emitter.send(SseEmitter.event()
                                            .name("accessed-domains")
                                            .data(payload));
                                } catch (IOException e) {
                                    log.error("Failed to send accessed domain for image definition {}", imageDefinitionId, e);
                                    emitter.completeWithError(e);
                                    return;
                                }
                            }
                        }
                    }

                    var buildStatus = imageDefinition.getBuildStatus();
                    if (buildStatus != null) {
                        int statusOrdinal = buildStatus.ordinal();
                        if (lastStatus.getAndSet(statusOrdinal) != statusOrdinal) {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("status")
                                        .data(buildStatus.name()));
                            } catch (IOException e) {
                                log.error("Failed to send status for image definition {}", imageDefinitionId, e);
                                emitter.completeWithError(e);
                                return;
                            }
                        }

                        if (buildStatus.isFinal()) {
                            if (domains == null || domains.isEmpty()) {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("no-domain-access")
                                            .data(""));
                                } catch (IOException e) {
                                    log.debug("Failed to send no-domain-access for image definition {}", imageDefinitionId, e);
                                }
                            }
                            if (startTime.plusMillis(minStreamingIntervalMs).isAfter(Instant.now())) {
                                Thread.sleep(minStreamingIntervalMs);
                            }
                            emitter.complete();
                            return;
                        }
                    }

                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                log.debug("Accessed domains streaming thread interrupted for image definition {}", imageDefinitionId);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to stream accessed domains for image definition {}", imageDefinitionId, e);
                emitter.completeWithError(e);
            }
        });

        return () -> future.cancel(true);
    }

    private SafeAutoCloseable startLogStreaming(UUID imageDefinitionId, SseEmitter emitter) {
        var logIndex = new AtomicInteger();
        var lastStatus = new AtomicInteger();
        var startTime = Instant.now();

        Future<?> future = executorService.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    var imageDefinition = imageDefinitionService.getImageDefinition(imageDefinitionId)
                            .orElseThrow(() -> new EntityNotFoundException("ImageDefinition not found: %s".formatted(imageDefinitionId)));

                    // log lines
                    var logs = imageDefinition.getBuildLogs();
                    if (logs != null) {
                        while (logIndex.get() < logs.size()) {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("logs")
                                        .data(logs.get(logIndex.getAndIncrement())));
                            } catch (IOException e) {
                                log.error("Failed to send log line for image definition {}", imageDefinitionId, e);
                                emitter.completeWithError(e);
                                return;
                            }
                        }
                    }

                    // status update
                    var buildStatus = imageDefinition.getBuildStatus();
                    if (buildStatus != null) {
                        var statusOrdinal = buildStatus.ordinal();
                        if (lastStatus.getAndSet(statusOrdinal) != statusOrdinal) {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("status")
                                        .data(buildStatus.name()));
                            } catch (IOException e) {
                                log.error("Failed to send status for image definition {}", imageDefinitionId, e);
                                emitter.completeWithError(e);
                                return;
                            }
                        }

                        // auto-complete on final status
                        if (buildStatus.isFinal()) {
                            if (startTime.plusMillis(minStreamingIntervalMs).isAfter(Instant.now())) {
                                log.debug("Image build log streaming {} has been running for less then 2 seconds", imageDefinitionId);
                                // Otherwise emitter may be closed even before spring attach handler on SssEmitter
                                // so onComplete callbacks will not be called
                                Thread.sleep(minStreamingIntervalMs);
                            }
                            emitter.complete();
                            return;
                        }
                    }

                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                log.debug("Log streaming thread interrupted for image definition {}", imageDefinitionId);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to stream logs for image definition {}", imageDefinitionId, e);
                emitter.completeWithError(e);
            }
        });
        
        return () -> future.cancel(true);
    }

    private SafeAutoCloseable startStatusStreaming(UUID imageDefinitionId, SseEmitter emitter) {
        var lastStatus = new AtomicInteger();

        Future<?> future = executorService.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    var imageDefinition = imageDefinitionService.getImageDefinition(imageDefinitionId)
                            .orElseThrow(() -> new EntityNotFoundException("ImageDefinition not found: %s".formatted(imageDefinitionId)));

                    var buildStatus = imageDefinition.getBuildStatus();
                    if (buildStatus != null) {
                        var statusOrdinal = buildStatus.ordinal();
                        if (lastStatus.getAndSet(statusOrdinal) != statusOrdinal) {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("status")
                                        .data(buildStatus.name()));
                            } catch (IOException e) {
                                log.error("Failed to send status for image definition {}", imageDefinitionId, e);
                                emitter.completeWithError(e);
                                return;
                            }
                        }
                        
                        if (buildStatus.isFinal()) {
                            emitter.complete();
                            return;
                        }
                    }

                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                log.debug("Status streaming thread interrupted for image definition {}", imageDefinitionId);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to stream status for image definition {}", imageDefinitionId, e);
                emitter.completeWithError(e);
            }
        });
        
        return () -> future.cancel(true);
    }

}
