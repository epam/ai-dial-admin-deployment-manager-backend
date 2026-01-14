package com.epam.aidial.deployment.manager.kubernetes.event;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class WatchEventReader implements AutoCloseable {

    private final String deploymentId;
    private final EventStreamerConfiguration config;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Watch watch;

    /**
     * Starts watching for events. The watch remains open until {@link #close()} is called.
     *
     * @param source NonNamespaceOperation for events (e.g., client.v1().events().inNamespace(namespace))
     * @param eventConsumer Consumer to process event
     */
    public void readEvents(NonNamespaceOperation<Event, EventList, Resource<Event>> source,
                           Consumer<Event> eventConsumer) {
        watch = source.watch(new Watcher<>() {
            @Override
            public void eventReceived(Action action, Event event) {
                log.debug("Received event: {}", event);
                if (isEventRelatedToDeployment(event) && matchesConfigFilters(event)) {
                    eventConsumer.accept(event);
                }
            }

            @Override
            public void onClose(WatcherException cause) {
                log.debug("Event watch closed for deployment '{}': {}", deploymentId, cause != null ? cause.getMessage() : "normal");
                closed.set(true);
            }
        });

        while (!closed.get()) {
            try {
                Thread.sleep(500); // Sleep briefly to avoid busy-waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void close() {
        if (watch != null) {
            watch.close();
        }
        closed.set(true);
    }

    private boolean isEventRelatedToDeployment(Event event) {
        return event.getInvolvedObject() != null
                && event.getInvolvedObject().getName() != null
                && event.getInvolvedObject().getName().contains(deploymentId);
    }

    private boolean matchesConfigFilters(Event event) {
        if (config.sinceTime() != null) {
            Instant firstTimestamp = Optional.ofNullable(event.getFirstTimestamp())
                    .flatMap(ts -> {
                        try {
                            return Optional.of(Instant.parse(ts));
                        } catch (Exception e) {
                            log.warn("Failed to parse firstTimestamp: {}", ts, e);
                            return Optional.empty();
                        }
                    }).orElse(null);
            if (firstTimestamp == null || firstTimestamp.isBefore(config.sinceTime())) {
                return false;
            }
        }

        if (config.eventType() != null
                && !StringUtils.equalsIgnoreCase(event.getType(), config.eventType().name())) {
            return false;
        }

        String eventKind = Optional.ofNullable(event.getInvolvedObject())
                .map(ObjectReference::getKind)
                .orElse(null);

        if (config.involvedObjectKind() != null
                && !StringUtils.equalsIgnoreCase(eventKind, config.involvedObjectKind().name())) {
            return false;
        }

        return true;
    }
}