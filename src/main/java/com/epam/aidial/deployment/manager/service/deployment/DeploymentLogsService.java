package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.configuration.HubbleRelayProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentDomainEntryRepository;
import com.epam.aidial.deployment.manager.exception.ValidationException;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderConfiguration;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderFactory;
import com.epam.aidial.deployment.manager.service.SafeAutoCloseable;
import com.epam.aidial.deployment.manager.service.SseEmitterFactory;
import com.epam.aidial.deployment.manager.web.dto.DomainEntryDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class DeploymentLogsService {

    private final PodLogReaderFactory podLogReaderFactory;
    @Qualifier("sse-streamer")
    private final ExecutorService executorService;
    private final SseEmitterFactory sseEmitterFactory;
    private final DeploymentManagerProvider deploymentManagerProvider;
    private final DeploymentDomainEntryRepository deploymentDomainEntryRepository;
    private final HubbleRelayProperties hubbleRelayProperties;
    private final ObjectMapper objectMapper;
    @Value("${app.sse.poll-interval-ms:1000}")
    private final long pollIntervalMs;

    public SseEmitter streamLogs(String id,
                                 String podName,
                                 PodLogReaderConfiguration cfg) {

        try {
            var containerResource = deploymentManagerProvider.provide(id)
                    .getContainerResourceForLogs(id, podName, cfg.previous());

            return sseEmitterFactory.createEmitter(
                    id,
                    "Deployment-" + podName,
                    emitter -> startPodStreaming(id, containerResource, cfg, emitter));

        } catch (ValidationException e) {
            log.info("Log streaming rejected for deployment '{}', pod '{}': {}", id, podName, e.getMessage());
            return sseEmitterFactory.createErrorEmitter(id, "Deployment-" + podName, e.getMessage());
        } catch (Exception e) {
            log.warn("Log streaming failed for deployment '{}', pod '{}'", id, podName, e);
            return sseEmitterFactory.createErrorEmitter(id, "Deployment-" + podName, "Unknown error occurred");
        }
    }

    private SafeAutoCloseable startPodStreaming(String id,
                                                ContainerResource containerResource,
                                                PodLogReaderConfiguration cfg,
                                                SseEmitter emitter) {

        var logFuture = executorService.submit(() -> {
            try {
                podLogReaderFactory.create(cfg)
                        .readLogs(containerResource, batch -> {
                            for (var line : batch) {
                                try {
                                    synchronized (emitter) {
                                        emitter.send(SseEmitter.event()
                                                .name("logs")
                                                .data(line));
                                    }
                                } catch (IOException e) {
                                    log.warn("Failed to send log line. Deployment {}", id, e);
                                    synchronized (emitter) {
                                        emitter.completeWithError(e);
                                    }
                                }
                            }
                        });
                synchronized (emitter) {
                    emitter.complete();
                }
            } catch (Exception e) {
                String message = "Failed to read logs for deployment " + id;
                if (e instanceof ClosedByInterruptException) {
                    log.warn("{}. Reason: ClosedByInterruptException", message);
                } else {
                    log.warn(message, e);
                }
                synchronized (emitter) {
                    emitter.completeWithError(e);
                }
            }
        });

        Future<?> domainFuture = null;
        if (hubbleRelayProperties.isEnabled()) {
            var domainIndex = new AtomicInteger(0);
            domainFuture = executorService.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        var entries = deploymentDomainEntryRepository.findAllByDeploymentId(id);
                        while (domainIndex.get() < entries.size()) {
                            var entry = entries.get(domainIndex.getAndIncrement());
                            try {
                                var data = objectMapper.writeValueAsString(
                                        new DomainEntryDto(entry.getDomain(), entry.getVerdict()));
                                synchronized (emitter) {
                                    emitter.send(SseEmitter.event()
                                            .name("domain")
                                            .data(data));
                                }
                            } catch (JsonProcessingException e) {
                                log.warn("Failed to serialize domain entry for deployment {}", id, e);
                            } catch (IOException e) {
                                log.warn("Failed to send domain entry for deployment {}", id, e);
                                synchronized (emitter) {
                                    emitter.completeWithError(e);
                                }
                                return;
                            }
                        }
                        Thread.sleep(pollIntervalMs);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("Domain polling failed for deployment {}", id, e);
                }
            });
        }

        final Future<?> finalDomainFuture = domainFuture;
        return () -> {
            logFuture.cancel(true);
            if (finalDomainFuture != null) {
                finalDomainFuture.cancel(true);
            }
        };
    }

}
