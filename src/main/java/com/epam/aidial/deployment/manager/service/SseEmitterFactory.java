package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class SseEmitterFactory {

    @Value("${app.sse.heartbeat.interval}")
    private final int heartbeatInterval;
    @Qualifier("sse-streamer")
    private final ExecutorService executorService;

    /**
     * Creates and returns an emitter that already has heartbeat and clean-up
     * handlers configured. The caller only supplies a function that starts the
     * “real” streaming work (logs, status, …).
     */
    public SseEmitter createEmitter(
            String businessId,
            String humanReadableId,
            Function<SseEmitter, SafeAutoCloseable> startStreaming) {

        var heartbeatRef = new AtomicReference<SafeAutoCloseable>();
        var streamingRef = new AtomicReference<SafeAutoCloseable>();

        Runnable cleanup = () -> closeSafely(heartbeatRef.get(), streamingRef.get());

        var emitter = new SseEmitter();
        emitter.onCompletion(() -> {
            log.debug("SSE completed. {}: {}", humanReadableId, businessId);
            cleanup.run();
        });
        emitter.onTimeout(() -> {
            log.debug("SSE failed on timeout. {}: {}", humanReadableId, businessId);
            cleanup.run();
        });
        emitter.onError(e -> {
            log.debug("SSE error. {}: {}", humanReadableId, businessId, e);
            cleanup.run();
        });

        heartbeatRef.set(startHeartbeat(businessId, humanReadableId, emitter));
        streamingRef.set(startStreaming.apply(emitter));

        return emitter;
    }

    /**
     * Creates an emitter that immediately sends an error event and completes.
     * Used when pre-flight validation fails before streaming can begin.
     */
    public SseEmitter createErrorEmitter(String businessId, String humanReadableId, String errorMessage) {
        var emitter = new SseEmitter();
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("message", errorMessage), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            log.warn("Failed to send error event. {}: {}", humanReadableId, businessId, e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private SafeAutoCloseable startHeartbeat(String id,
                                             String humanReadableId,
                                             SseEmitter emitter) {
        Future<?> future = executorService.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(heartbeatInterval);
                    synchronized (emitter) {
                        emitter.send(SseEmitter.event().name("heartbeat"));
                    }
                }
            } catch (InterruptedException e) {
                log.debug("Heartbeat thread interrupted. {}: {}", humanReadableId, id);
                Thread.currentThread().interrupt();
            } catch (AsyncRequestNotUsableException asyncRequestNotUsableException) {
                log.warn("Failed to send heartbeat. Client closed connection. {}: {}", humanReadableId, id);
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to send heartbeat. {}: {}", humanReadableId, id, e);
                emitter.completeWithError(e);
            }
        });
        return () -> future.cancel(true);
    }

    private static void closeSafely(SafeAutoCloseable... toClose) {
        for (SafeAutoCloseable c : toClose) {
            if (Objects.nonNull(c)) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    log.warn("Failed to close {}", c);
                }
            }
        }
    }

}
