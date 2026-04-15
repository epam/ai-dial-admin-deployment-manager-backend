package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.ValidationException;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderConfiguration;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderFactory;
import com.epam.aidial.deployment.manager.service.SafeAutoCloseable;
import com.epam.aidial.deployment.manager.service.SseEmitterFactory;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.ExecutorService;

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

    public SseEmitter streamLogs(String id,
                                 String podName,
                                 PodLogReaderConfiguration cfg,
                                 String containerName) {

        try {
            var containerResource = deploymentManagerProvider.provide(id)
                    .getContainerResourceForLogs(id, podName, containerName, cfg.previous());

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

        var future = executorService.submit(() -> {
            try {
                podLogReaderFactory.create(cfg)
                        .readLogs(containerResource, batch -> {
                            for (var line : batch) {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("logs")
                                            .data(line));
                                } catch (IOException e) {
                                    log.warn("Failed to send log line. Deployment {}", id, e);
                                    emitter.completeWithError(e);
                                }
                            }
                        });
                emitter.complete();
            } catch (Exception e) {
                String message = "Failed to read logs for deployment " + id;
                if (e instanceof ClosedByInterruptException) {
                    log.warn("{}. Reason: ClosedByInterruptException", message);
                } else {
                    log.warn(message, e);
                }
                emitter.completeWithError(e);
            }
        });
        return () -> future.cancel(true);
    }

}
