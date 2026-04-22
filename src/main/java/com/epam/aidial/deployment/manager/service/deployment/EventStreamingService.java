package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.UnknownObjectKindException;
import com.epam.aidial.deployment.manager.kubernetes.event.EventReaderFactory;
import com.epam.aidial.deployment.manager.kubernetes.event.EventStreamerConfiguration;
import com.epam.aidial.deployment.manager.mapper.EventInfoMapper;
import com.epam.aidial.deployment.manager.service.SafeAutoCloseable;
import com.epam.aidial.deployment.manager.service.SseEmitterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class EventStreamingService {

    @Qualifier("sse-streamer")
    private final ExecutorService executorService;
    private final SseEmitterFactory sseEmitterFactory;
    private final EventReaderFactory eventReaderFactory;
    private final EventInfoMapper eventInfoMapper;
    private final DeploymentManagerProvider deploymentManagerProvider;

    public SseEmitter streamEvents(String id, EventStreamerConfiguration cfg) {
        return sseEmitterFactory.createEmitter(
                id,
                "Deployment-" + id,
                emitter -> startEventStreaming(id, emitter, cfg)
        );
    }

    private SafeAutoCloseable startEventStreaming(String id, SseEmitter emitter, EventStreamerConfiguration cfg) {
        var eventSource = deploymentManagerProvider.provide(id).getAllEventsBase();
        var eventReader = eventReaderFactory.create(id, cfg);
        var future = executorService.submit(() -> {
            try {
                eventReader.readEvents(eventSource, event -> {
                    try {
                        var eventInfo = eventInfoMapper.toEventInfo(event, id);
                        synchronized (emitter) {
                            emitter.send(SseEmitter.event()
                                    .name("event")
                                    .data(eventInfo));
                        }
                    } catch (UnknownObjectKindException unknownObjectKindException) {
                        log.debug("Failed to parse involved object kind. Deployment {}. Reason: {}. Skipping event: {}",
                                id, unknownObjectKindException.getMessage(), event);
                    } catch (DataIntegrityViolationException dataIntegrityViolationException) {
                        log.warn("Failed to send event. Deployment {} was deleted", id);
                        emitter.completeWithError(dataIntegrityViolationException);
                    } catch (AsyncRequestNotUsableException asyncRequestNotUsableException) {
                        log.debug("Client disconnected during event streaming. Deployment {}", id);
                        emitter.complete();
                    } catch (Exception e) {
                        log.warn("Failed to send event. Deployment {}", id, e);
                        emitter.completeWithError(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("Failed to stream events for deployment {}", id, e);
                emitter.completeWithError(e);
            } finally {
                eventReader.close();
            }
        });
        return () -> future.cancel(true);
    }
}