package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
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
import java.util.UUID;
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

    public SseEmitter streamLogs(UUID id,
                                 String podName,
                                 PodLogReaderConfiguration cfg) {

        var containerResource = deploymentManagerProvider.provide(id)
                .getContainerResource(id, podName);

        if (containerResource == null) {
            throw new EntityNotFoundException("Pod not found. Deployment=%s Pod=%s".formatted(id, podName));
        }

        return sseEmitterFactory.createEmitter(
                id,
                "Deployment-" + podName,
                emitter -> startPodStreaming(id, containerResource, cfg, emitter));
    }

    private SafeAutoCloseable startPodStreaming(UUID id,
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
                                    log.error("Failed to send log line. Deployment {}", id, e);
                                    emitter.completeWithError(e);
                                }
                            }
                        });
                emitter.complete();
            } catch (Exception e) {
                log.error("Failed to read logs for deployment {}", id, e);
                emitter.completeWithError(e);
            }
        });
        return () -> future.cancel(true);
    }

}
