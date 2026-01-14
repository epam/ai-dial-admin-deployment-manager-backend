package com.epam.aidial.deployment.manager.kubernetes.event;

import com.epam.aidial.deployment.manager.model.EventType;
import com.epam.aidial.deployment.manager.model.ObjectKind;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "ResultOfMethodCallIgnored"})
@ExtendWith(MockitoExtension.class)
class WatchEventReaderTest {

    private static final String DEPLOYMENT_ID = UUID.randomUUID().toString();
    private static final ObjectKind POD_KIND = ObjectKind.POD;
    private static final ObjectKind SERVICE_KIND = ObjectKind.SERVICE;
    private static final String POD_NAME = "pod-" + DEPLOYMENT_ID + "-xyz";
    private static final String UNRELATED_POD_NAME = "unrelated-pod";
    private static final String CREATION_TIMESTAMP = "2023-01-01T12:00:00Z";

    @Mock
    private NonNamespaceOperation<Event, EventList, Resource<Event>> eventSource;
    @Mock
    private Consumer<Event> eventConsumer;
    @Mock
    private Watch watch;

    private WatchEventReader reader;
    private EventStreamerConfiguration config;
    private Watcher<Event> capturedWatcher;

    @BeforeEach
    void setUp() {
        config = EventStreamerConfiguration.builder().build();
        reader = new WatchEventReader(DEPLOYMENT_ID, config);

        when(eventSource.watch(any(Watcher.class))).thenAnswer(invocation -> {
            capturedWatcher = invocation.getArgument(0);
            return watch;
        });
    }

    @Test
    void readEvents_shouldFilterEventsByDeploymentId() throws InterruptedException {
        // Given
        var relatedEvent = createEvent(POD_NAME, POD_KIND.name(), EventType.NORMAL.name(), CREATION_TIMESTAMP);
        var unrelatedEvent = createEvent(UNRELATED_POD_NAME, POD_KIND.name(), EventType.NORMAL.name(), CREATION_TIMESTAMP);

        var latch = new CountDownLatch(1);

        // When
        var watchThread = new Thread(() -> {
            reader.readEvents(eventSource, eventConsumer);
            latch.countDown();
        });
        watchThread.start();

        // Wait for the watcher to be registered
        Thread.sleep(100);

        // Simulate events being received
        capturedWatcher.eventReceived(Watcher.Action.ADDED, relatedEvent);
        capturedWatcher.eventReceived(Watcher.Action.ADDED, unrelatedEvent);

        // Close the reader to end the watch loop
        reader.close();

        // Wait for the watch thread to complete
        latch.await(1, TimeUnit.SECONDS);

        // Then
        verify(eventConsumer).accept(relatedEvent);
        verify(eventConsumer, never()).accept(unrelatedEvent);
    }

    @Test
    void readEvents_shouldFilterByEventType() throws InterruptedException {
        // Given
        config = EventStreamerConfiguration.builder()
                .eventType(EventType.NORMAL)
                .build();
        reader = new WatchEventReader(DEPLOYMENT_ID, config);

        var normalEvent = createEvent(POD_NAME, POD_KIND.name(), EventType.NORMAL.name(), CREATION_TIMESTAMP);
        var warningEvent = createEvent(POD_NAME, POD_KIND.name(), EventType.WARNING.name(), CREATION_TIMESTAMP);

        var latch = new CountDownLatch(1);

        // When
        var watchThread = new Thread(() -> {
            reader.readEvents(eventSource, eventConsumer);
            latch.countDown();
        });
        watchThread.start();

        // Wait for the watcher to be registered
        Thread.sleep(100);

        // Simulate events being received
        capturedWatcher.eventReceived(Watcher.Action.ADDED, normalEvent);
        capturedWatcher.eventReceived(Watcher.Action.ADDED, warningEvent);

        // Close the reader to end the watch loop
        reader.close();

        // Wait for the watch thread to complete
        latch.await(1, TimeUnit.SECONDS);

        // Then
        verify(eventConsumer).accept(normalEvent);
        verify(eventConsumer, never()).accept(warningEvent);
    }

    @Test
    void readEvents_shouldFilterByInvolvedObjectKind() throws InterruptedException {
        // Given
        config = EventStreamerConfiguration.builder()
                .involvedObjectKind(POD_KIND)
                .build();
        reader = new WatchEventReader(DEPLOYMENT_ID, config);

        var podEvent = createEvent(POD_NAME, POD_KIND.name(), EventType.NORMAL.name(), CREATION_TIMESTAMP);
        var serviceEvent = createEvent(POD_NAME, SERVICE_KIND.name(), EventType.NORMAL.name(), CREATION_TIMESTAMP);

        var latch = new CountDownLatch(1);

        // When
        var watchThread = new Thread(() -> {
            reader.readEvents(eventSource, eventConsumer);
            latch.countDown();
        });
        watchThread.start();

        // Wait for the watcher to be registered
        Thread.sleep(100);

        // Simulate events being received
        capturedWatcher.eventReceived(Watcher.Action.ADDED, podEvent);
        capturedWatcher.eventReceived(Watcher.Action.ADDED, serviceEvent);

        // Close the reader to end the watch loop
        reader.close();

        // Wait for the watch thread to complete
        latch.await(1, TimeUnit.SECONDS);

        // Then
        verify(eventConsumer).accept(podEvent);
        verify(eventConsumer, never()).accept(serviceEvent);
    }

    @Test
    void readEvents_shouldFilterBySinceTime() throws InterruptedException {
        // Given
        var sinceTime = Instant.parse("2023-01-01T11:00:00Z");
        config = EventStreamerConfiguration.builder()
                .sinceTime(sinceTime)
                .build();
        reader = new WatchEventReader(DEPLOYMENT_ID, config);

        var newerEvent = createEvent(POD_NAME, POD_KIND.name(), EventType.NORMAL.name(), CREATION_TIMESTAMP);
        var olderEvent = createEvent(POD_NAME, POD_KIND.name(), EventType.NORMAL.name(), "2022-12-31T23:59:59Z");

        var latch = new CountDownLatch(1);

        // When
        var watchThread = new Thread(() -> {
            reader.readEvents(eventSource, eventConsumer);
            latch.countDown();
        });
        watchThread.start();

        // Wait for the watcher to be registered
        Thread.sleep(100);

        // Simulate events being received
        capturedWatcher.eventReceived(Watcher.Action.ADDED, newerEvent);
        capturedWatcher.eventReceived(Watcher.Action.ADDED, olderEvent);

        // Close the reader to end the watch loop
        reader.close();

        // Wait for the watch thread to complete
        latch.await(1, TimeUnit.SECONDS);

        // Then
        verify(eventConsumer).accept(newerEvent);
        verify(eventConsumer, never()).accept(olderEvent);
    }

    @Test
    void readEvents_shouldHandleInvalidTimestamps() throws InterruptedException {
        // Given
        var sinceTime = Instant.parse("2023-01-01T11:00:00Z");
        config = EventStreamerConfiguration.builder()
                .sinceTime(sinceTime)
                .build();
        reader = new WatchEventReader(DEPLOYMENT_ID, config);

        var validEvent = createEvent(POD_NAME, POD_KIND.name(), EventType.NORMAL.name(), CREATION_TIMESTAMP);
        var invalidEvent = createEvent(POD_NAME, POD_KIND.name(), EventType.NORMAL.name(), "invalid-timestamp");

        var latch = new CountDownLatch(1);

        // When
        var watchThread = new Thread(() -> {
            reader.readEvents(eventSource, eventConsumer);
            latch.countDown();
        });
        watchThread.start();

        // Wait for the watcher to be registered
        Thread.sleep(100);

        // Simulate events being received
        capturedWatcher.eventReceived(Watcher.Action.ADDED, validEvent);
        capturedWatcher.eventReceived(Watcher.Action.ADDED, invalidEvent);

        // Close the reader to end the watch loop
        reader.close();

        // Wait for the watch thread to complete
        latch.await(1, TimeUnit.SECONDS);

        // Then
        verify(eventConsumer).accept(validEvent);
        verify(eventConsumer, never()).accept(invalidEvent);
    }

    @Test
    void readEvents_shouldHandleNullInvolvedObject() throws InterruptedException {
        // Given
        var validEvent = createEvent(POD_NAME, POD_KIND.name(), EventType.NORMAL.name(), CREATION_TIMESTAMP);
        var eventWithNullInvolvedObject = new EventBuilder()
                .withType(EventType.NORMAL.name())
                .withFirstTimestamp(CREATION_TIMESTAMP)
                .withInvolvedObject(null)
                .build();

        var latch = new CountDownLatch(1);

        // When
        var watchThread = new Thread(() -> {
            reader.readEvents(eventSource, eventConsumer);
            latch.countDown();
        });
        watchThread.start();

        // Wait for the watcher to be registered
        Thread.sleep(100);

        // Simulate events being received
        capturedWatcher.eventReceived(Watcher.Action.ADDED, validEvent);
        capturedWatcher.eventReceived(Watcher.Action.ADDED, eventWithNullInvolvedObject);

        // Close the reader to end the watch loop
        reader.close();

        // Wait for the watch thread to complete
        latch.await(1, TimeUnit.SECONDS);

        // Then
        verify(eventConsumer).accept(validEvent);
        verify(eventConsumer, never()).accept(eventWithNullInvolvedObject);
    }

    @Test
    void readEvents_shouldHandleNullObjectName() throws InterruptedException {
        // Given
        var validEvent = createEvent(POD_NAME, POD_KIND.name(), EventType.NORMAL.name(), CREATION_TIMESTAMP);
        var eventWithNullObjectName = createEvent(null, POD_KIND.name(), EventType.NORMAL.name(), CREATION_TIMESTAMP);

        var latch = new CountDownLatch(1);

        // When
        var watchThread = new Thread(() -> {
            reader.readEvents(eventSource, eventConsumer);
            latch.countDown();
        });
        watchThread.start();

        // Wait for the watcher to be registered
        Thread.sleep(100);

        // Simulate events being received
        capturedWatcher.eventReceived(Watcher.Action.ADDED, validEvent);
        capturedWatcher.eventReceived(Watcher.Action.ADDED, eventWithNullObjectName);

        // Close the reader to end the watch loop
        reader.close();

        // Wait for the watch thread to complete
        latch.await(1, TimeUnit.SECONDS);

        // Then
        verify(eventConsumer).accept(validEvent);
        verify(eventConsumer, never()).accept(eventWithNullObjectName);
    }

    @Test
    void readEvents_shouldHandleWatcherClose() throws InterruptedException {
        // Given
        var latch = new CountDownLatch(1);

        // When
        var watchThread = new Thread(() -> {
            reader.readEvents(eventSource, eventConsumer);
            latch.countDown();
        });
        watchThread.start();

        // Wait for the watcher to be registered
        Thread.sleep(100);

        // Simulate watcher close
        capturedWatcher.onClose(null); // Normal close

        // Wait for the watch thread to complete
        latch.await(1, TimeUnit.SECONDS);

        // Then
        verify(eventConsumer, never()).accept(any());
    }

    @Test
    void readEvents_shouldHandleWatcherCloseWithException() throws InterruptedException {
        // Given
        var latch = new CountDownLatch(1);

        // When
        var watchThread = new Thread(() -> {
            reader.readEvents(eventSource, eventConsumer);
            latch.countDown();
        });
        watchThread.start();

        // Wait for the watcher to be registered
        Thread.sleep(100);

        // Simulate watcher close with exception
        var exception = mock(WatcherException.class);
        when(exception.getMessage()).thenReturn("Test exception");
        capturedWatcher.onClose(exception);

        // Wait for the watch thread to complete
        latch.await(1, TimeUnit.SECONDS);

        // Then
        verify(eventConsumer, never()).accept(any());
    }

    private Event createEvent(String podName, String kind, String eventType, String timestamp) {
        var involvedObject = new ObjectReferenceBuilder()
                .withKind(kind)
                .withName(podName)
                .build();
        return new EventBuilder()
                .withType(eventType)
                .withFirstTimestamp(timestamp)
                .withInvolvedObject(involvedObject)
                .build();
    }
}